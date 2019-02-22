// Copyright 2017, Yahoo Holdings Inc.
// Licensed under the terms of the Apache License 2.0. Please see LICENSE file in project root for terms.
package com.yahoo.maha.core.query

import com.yahoo.maha.core._
import com.yahoo.maha.core.dimension._
import com.yahoo.maha.core.fact.{Fact, FactBestCandidate, FactCol, PublicFact}
import com.yahoo.maha.core.query.Version.{v0, v1, v2}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Created by jians on 10/20/15.
 */

class QueryBuilderContext {
  private[this] var aliasIndex = 0
  private[this] val tableAliasMap = new mutable.HashMap[String, String]
  // from alias -> tablename.alias mapping
  private[this] val colAliasToFactColNameMap = new mutable.HashMap[String, String]
  private[this] val colAliasToFactColExpressionMap = new mutable.HashMap[String, String]
  private[this] val colAliasToDimensionColNameMap = new mutable.HashMap[String, String]

  private[this] val colAliasToDimensionMap = new mutable.HashMap[String, PublicDimension]

  private[this] val factAliasToColumnMap = new mutable.HashMap[String, Column]()
  private[this] val dimensionAliasToColumnMap = new mutable.HashMap[String, DimensionColumn]()

  private[this] val preOuterAliasToColumnMap = new mutable.HashMap[String, Column]()
  private[this] val preOuterFinalAliasToAliasMap = new mutable.HashMap[String, String]()

  private[this] val publicDimensionAliasTupleToFinalAlias = new mutable.HashMap[(PublicDimension,String), String]()

  private[this] val columnNames = new mutable.TreeSet[String]

  def getAliasForTable(name: String) : String = {
    tableAliasMap.get(name) match {
      case None => {
        val alias = s"${name.split("_").map(_.substring(0, 1)).mkString("")}$aliasIndex"
        aliasIndex += 1
        tableAliasMap += name -> alias
        alias
      }
      case Some(alias) => alias
    }
  }

  def getAliasForField(TableName: String, fieldName: String) : String = {
    tableAliasMap.get(TableName) match {
      case None => {
        val alias = s"${TableName.split("_").map(_.substring(0, 1)).mkString("")}$aliasIndex"
        aliasIndex += 1
        tableAliasMap += TableName -> alias
        s"${alias}_$fieldName"
      }
      case Some(alias) => s"${alias}_$fieldName"
    }
  }
  
  def getDimensionColByAlias(alias: String) : DimensionColumn = {
    dimensionAliasToColumnMap(alias)
  }

  def isDimensionCol(alias: String) : Boolean = {
    dimensionAliasToColumnMap.contains(alias)
  }

  def containsDimensionAliasToColumnMap(alias: String) : Boolean = {
    dimensionAliasToColumnMap.contains(alias)
  }

  def getFactColByAlias(alias: String) : Column = {
    factAliasToColumnMap(alias)
  }

  def getDimensionForColAlias(alias: String) : PublicDimension = {
    colAliasToDimensionMap(alias)
  }

  def setDimensionColAlias(alias: String, finalAlias: String, dimensionColumn: DimensionColumn, pubDim: PublicDimension): Unit = {
    dimensionAliasToColumnMap.put(alias, dimensionColumn)
    colAliasToDimensionColNameMap.put(alias, finalAlias)
    setColAliasAndPublicDimAlias(alias, finalAlias, dimensionColumn, pubDim)
  }

  def setDimensionColAliasForDimOnlyQuery(alias: String, finalAlias: String, dimensionColumn: DimensionColumn, pubDim: PublicDimension): Unit = {
    if (!dimensionAliasToColumnMap.contains(alias)) {
      setDimensionColAlias(alias, finalAlias, dimensionColumn, pubDim)
    } else {
      if (dimensionAliasToColumnMap(alias).isForeignKey) {
        dimensionAliasToColumnMap.put(alias, dimensionColumn)
        colAliasToDimensionColNameMap.put(alias, finalAlias)
      }
      setColAliasAndPublicDimAlias(alias, finalAlias, dimensionColumn, pubDim)
    }
  }

  def setColAliasAndPublicDimAlias(alias: String, finalAlias: String, dimensionColumn: DimensionColumn, pubDim: PublicDimension) : Unit = {
    columnNames += dimensionColumn.name
    colAliasToDimensionMap.put(alias, pubDim)
    publicDimensionAliasTupleToFinalAlias.put((pubDim, alias), finalAlias)
  }

  def getPublicDimensionAliasTupleToFinalAlias (pubDim: PublicDimension, alias: String): Option[String] = {
    publicDimensionAliasTupleToFinalAlias.get((pubDim,alias))
  }

  def setFactColAliasAndExpression(alias: String, finalAlias: String, factColumn: Column, exp: Option[String]): Unit = {
    colAliasToFactColNameMap.put(alias, finalAlias)
    if(exp.isDefined) {
      colAliasToFactColExpressionMap.put(alias, exp.get)
    }
    factAliasToColumnMap.put(alias, factColumn)
    columnNames += factColumn.name
  }

  def setFactColAlias(alias: String, finalAlias: String, factColumn: Column): Unit = {
    colAliasToFactColNameMap.put(alias, finalAlias)
    factAliasToColumnMap.put(alias, factColumn)
    columnNames += factColumn.name
  }
  
  def markDuplicateFactCol(alias: String, finalAlias: String, factColumn: Column): Unit = {
    colAliasToFactColNameMap.put(alias, finalAlias)
    factAliasToColumnMap.put(alias, factColumn)
    columnNames += factColumn.name
  }

  def getDimensionColNameForAlias(alias: String) : String = {
    require(colAliasToDimensionColNameMap.contains(alias), s"dim alias does not exist in inner selection : $alias")
    colAliasToDimensionColNameMap(alias)
  }

  def getFactColNameForAlias(alias: String) : String = {
    require(colAliasToFactColNameMap.contains(alias), s"fact alias does not exist in inner selection : $alias")
    colAliasToFactColNameMap(alias)
  }

  def getFactColExpressionOrNameForAlias(alias: String) : String = {
    require(colAliasToFactColNameMap.contains(alias), s"fact alias does not exist in inner selection : $alias")
    colAliasToFactColExpressionMap.getOrElse(alias,colAliasToFactColNameMap(alias))
  }

  def setPreOuterAliasToColumnMap(alias:String, finalAlias: String, column: Column): Unit = {
    preOuterAliasToColumnMap.put(finalAlias, column)
    preOuterFinalAliasToAliasMap.put(finalAlias, alias)
  }

  def getPreOuterFinalAliasToAliasMap(alias :String): Option[String] = {
    preOuterFinalAliasToAliasMap.get(alias)
  }

  def getPreOuterAliasToColumnMap(alias: String): Option[Column] = {
    preOuterAliasToColumnMap.get(alias)
  }

  def containsPreOuterAlias(alias: String): Boolean = {
    preOuterAliasToColumnMap.contains(alias)
  }

  def containsFactColNameForAlias(alias: String): Boolean = {
    colAliasToFactColNameMap.contains(alias)
  }

  def containsFactAliasToColumnMap(alias :String) : Boolean = {
    factAliasToColumnMap.contains(alias)
  }
  
  def containsColByName(name: String) : Boolean = columnNames.contains(name)

  def aliasColumnMap : Map[String, Column] = dimensionAliasToColumnMap.toMap ++ factAliasToColumnMap.toMap

  def getColAliasToFactColNameMap : scala.collection.Map[String, String] = colAliasToFactColNameMap
}

trait QueryGenerator[T <: EngineRequirement] {
  def generate(queryContext: QueryContext): Query
  def engine: Engine
  def validateEngineConstraints(requestModel: RequestModel): Boolean = true
  def version: Version = Version.DEFAULT
}

trait BaseQueryGenerator[T <: EngineRequirement] extends QueryGenerator[T] {



  def removeDuplicateIfForced(localFilters: Seq[Filter], forcedFilters: Seq[ForcedFilter], inputContext: FactualQueryContext): Array[Filter] = {
    val queryContext = inputContext

    val fact = queryContext.factBestCandidate.fact
    val returnedFilters = new mutable.LinkedHashMap[String, Filter]
    localFilters.foreach {
      filter =>
        if(filter.field != "or") {
          val name = queryContext.factBestCandidate.publicFact.aliasToNameColumnMap(filter.field)
          val column = fact.columnsByNameMap(name)
          val real_name = column.alias.getOrElse(name)
          returnedFilters(real_name) = filter
        } else {
          returnedFilters(filter.field) = filter
        }
    }
    forcedFilters.foreach {
      filter =>
        val name = queryContext.factBestCandidate.publicFact.aliasToNameColumnMap(filter.field)
        val column = fact.columnsByNameMap(name)
        val real_name = column.alias.getOrElse(name)
        if (!filter.isOverridable || !returnedFilters.contains(real_name)) {
          returnedFilters(real_name) = filter
        }
    }
    returnedFilters.values.toArray
  }
}

object QueryGeneratorHelper {
  def populateAliasColMapOfRequestCols(columnInfo: ColumnInfo
                                       , queryBuilderContext: QueryBuilderContext
                                       , queryContext : CombinedQueryContext) : Map[String, Column] = {
    if (!columnInfo.isInstanceOf[ConstantColumnInfo] && queryBuilderContext.aliasColumnMap.contains(columnInfo.alias)) {
      Map(columnInfo.alias -> queryBuilderContext.aliasColumnMap(columnInfo.alias))
    } else if (queryContext.factBestCandidate.duplicateAliasMapping.contains(columnInfo.alias)) {
      val sourceAliases = queryContext.factBestCandidate.duplicateAliasMapping(columnInfo.alias)
      val sourceAlias = sourceAliases.find(queryBuilderContext.aliasColumnMap.contains)
      require(sourceAlias.isDefined
        , s"Failed to find source column for duplicate alias mapping : ${queryContext.factBestCandidate.duplicateAliasMapping(columnInfo.alias)}")
      Map(columnInfo.alias -> queryBuilderContext.aliasColumnMap(sourceAlias.get))
    }
    else {
      Map.empty
    }
  }

  def handleOuterFactColInfo(queryBuilderContext: QueryBuilderContext
                        , alias : String
                        , factCandidate : FactBestCandidate
                        , renderFactCol: (String, String, Column, String) => String
                        , duplicateAliasMapping: Map[String, Set[String]]
                        , tableAlias : String
                        , isOuterGroupBy: Boolean) : String = {
    if (queryBuilderContext.containsFactColNameForAlias(alias)) {
      val col = queryBuilderContext.getFactColByAlias(alias)
      val finalAlias = queryBuilderContext.getFactColNameForAlias(alias)
      val finalAliasOrExpression = {
        if(queryBuilderContext.isDimensionCol(alias) && !isOuterGroupBy) {
          val factAlias = queryBuilderContext.getAliasForTable(tableAlias)
          val factExp = queryBuilderContext.getFactColExpressionOrNameForAlias(alias)
          s"$factAlias.$factExp"
        } else  {
          queryBuilderContext.getFactColExpressionOrNameForAlias(alias)
        }
      }
      renderFactCol(alias, finalAliasOrExpression, col, finalAlias)
    } else if (duplicateAliasMapping.contains(alias)) {
      val duplicateAliases = duplicateAliasMapping(alias)
      val renderedDuplicateAlias = duplicateAliases.collectFirst {
        case duplicateAlias if queryBuilderContext.containsFactColNameForAlias(duplicateAlias) =>
          val col = queryBuilderContext.getFactColByAlias(duplicateAlias)
          val finalAliasOrExpression = queryBuilderContext.getFactColExpressionOrNameForAlias(duplicateAlias)
          val finalAlias = queryBuilderContext.getFactColNameForAlias(duplicateAlias)
          renderFactCol(alias, finalAliasOrExpression, col, finalAlias)
      }
      require(renderedDuplicateAlias.isDefined, s"Failed to render column : $alias")
      renderedDuplicateAlias.get
    } else {
      throw new IllegalArgumentException(s"Could not find inner alias for outer column : $alias")
    }
  }

  /**
    * Handle filter name extraction & column function based on filter type:
    * multi field forced, combining (or, and), or single-field.
    */
  def handleFilterTypeNameExpressionExtraction(filter: Filter
                                              , colFn: Column => String
                                              , fact: Fact
                                              , publicFact: PublicFact): Map[String, (String, String)] = {
    val filterFields: mutable.ListBuffer[String]  = new mutable.ListBuffer[String]()
    val expressionsMap: mutable.HashMap[String, (String, String)] = new mutable.HashMap[String, (String, String)]()
    filter match {
      case filter1: OrFilter => filterFields ++= filter1.filters.map(f => f.field)
      case filter1: MultiFieldForcedFilter => filterFields ++= List(filter1.field, filter1.compareTo)
      case _ => filterFields ++= List(filter.field)
    }

    val filterBaseNames: List[(String, String)] = filterFields.map(f => (f, publicFact.aliasToNameColumnMap(f))).toList
    if(filterBaseNames.forall(f=> fact.dimColMap.contains(f._2))) Map.empty
    else if(filterBaseNames.forall(f=> fact.factColMap.contains(f._2)))  {
      for (f <- filterBaseNames) {
        val column = fact.columnsByNameMap(f._2)
        val exp = colFn(column)
        expressionsMap(f._1) = (f._2, exp)
      }
      expressionsMap.toMap
    } else {
      throw new IllegalArgumentException(
        s"Unknown fact column: publicFact=${publicFact.name}, fact=${fact.name} alias=${filter.field}, name=$filterBaseNames")
    }

  }

  def handleFilterRender(filter: Filter,
                         publicFact: PublicFact,
                         fact: Fact,
                         aliasToNameMapFull: Map[String, String],
                         queryContext: CombinedQueryContext,
                         engine: Engine,
                         literalMapper: LiteralMapper,
                         colFn: Column => String): SqlResult = {
    val inMap: Map[String, (String, String)] = handleFilterTypeNameExpressionExtraction(filter, colFn, fact, publicFact)

    FilterSql.renderFilter(
      filter,
      aliasToNameMapFull,
      inMap,
      fact.columnsByNameMap,
      engine,
      literalMapper
    )

    /*val fieldNames: mutable.ArrayBuffer[String] = new ArrayBuffer[String]()
    val isMultiField: Boolean = filter.isInstanceOf[MultiFieldForcedFilter]

    if(!filter.isInstanceOf[CombiningFilter])
      fieldNames += publicFact.aliasToNameColumnMap(filter.field)
    else if(filter.isInstanceOf[OrFilter])

    if(isMultiField)
      fieldNames += publicFact.aliasToNameColumnMap(filter.asInstanceOf[MultiFieldForcedFilter].compareTo)

    val baseFieldName = fieldNames.remove(0)
    if (fact.dimColMap.contains(baseFieldName)) {

      FilterSql.renderFilter(
        filter,
        aliasToNameMapFull,
        Map.empty,
        fact.columnsByNameMap,
        engine,
        literalMapper
      )

    } else if (fact.factColMap.contains(baseFieldName)) {
      val column = fact.columnsByNameMap(baseFieldName)
      val exp = colFn(column)

      if(!isMultiField) {
        FilterSql.renderFilter(
          filter,
          queryContext.factBestCandidate.publicFact.aliasToNameColumnMap,
          Map(filter.field -> (baseFieldName, exp)),
          fact.columnsByNameMap,
          engine,
          literalMapper
        )
      } else {
        val multiFieldForcedFilter = filter.asInstanceOf[MultiFieldForcedFilter]
        val compareToFieldName = fieldNames.remove(0)
        val compareToColumn = fact.columnsByNameMap(compareToFieldName)
        val secondExp = colFn(compareToColumn)
        FilterSql.renderFilter(
          filter,
          queryContext.factBestCandidate.publicFact.aliasToNameColumnMap,
          Map(multiFieldForcedFilter.field -> (baseFieldName, exp), multiFieldForcedFilter.compareTo -> (compareToColumn.alias.getOrElse(compareToColumn.name), secondExp)),
          fact.columnsByNameMap,
          engine,
          literalMapper
        )
      }

    } else {
      throw new IllegalArgumentException(
        s"Unknown fact column: publicFact=${publicFact.name}, fact=${fact.name} alias=${filter.field}, name=$baseFieldName")
    }*/
  }
}

sealed trait VersionNumber {
  def number: Int
}
case class Version private(number: Int) extends VersionNumber {
  require(!Version.versions.contains(number), s"Version $number already exists: ${Version.versions}")
  Version.versions.+=((number, this))
}

object Version {
  private val versions: mutable.Map[Int, Version] = new mutable.HashMap[Int, Version]()
  val v0: Version = Version(0)
  val v1: Version = Version(1)
  val v2: Version = Version(2)
  val DEFAULT = v0

  def from(number: Int) : Option[Version] = {
    versions.get(number)
  }
}

class QueryGeneratorRegistry {

  private[this] var queryGeneratorRegistry : Map[Engine, Map[Version , QueryGenerator[_]]] = Map.empty

  def isEngineRegistered(engine: Engine, version: Option[Version]): Boolean = synchronized {
    queryGeneratorRegistry.contains(engine) && queryGeneratorRegistry(engine).contains(version.getOrElse(Version.DEFAULT))
  }

  def register[U <: QueryGenerator[_]](engine: Engine, qg: U, version: Version = Version.DEFAULT) : Unit = synchronized {
    require(!isEngineRegistered(engine, Option(version)), s"Query generator already defined for engine : $engine and version $version")
    if (queryGeneratorRegistry.contains(engine)) {
      queryGeneratorRegistry += (engine -> (Map(version -> qg) ++ queryGeneratorRegistry(engine)))
    } else {
      queryGeneratorRegistry += (engine -> Map(version -> qg))
    }
  }

  def getDefaultGenerator(engine: Engine): Option[QueryGenerator[EngineRequirement]] = {
    for {
      versionGeneratorMap <- queryGeneratorRegistry.get(engine)
      generator <- versionGeneratorMap.get(Version.DEFAULT).asInstanceOf[Option[QueryGenerator[EngineRequirement]]]
    } yield generator
  }

  // This method validates engine constraints and falls back to default generator if validation fails
  def getValidGeneratorForVersion(engine: Engine, version: Version, requestModel: Option[RequestModel]): Option[QueryGenerator[EngineRequirement]] = {
    for {
      versionGeneratorMap <- queryGeneratorRegistry.get(engine)
      generator <- {
        val generator = versionGeneratorMap.get(version)
        val isValidationNeeded = requestModel.isDefined && generator.isDefined
        if (!isValidationNeeded || generator.get.validateEngineConstraints(requestModel.get)) {
          generator
        } else {
          versionGeneratorMap.get(Version.DEFAULT)
        }
      }.asInstanceOf[Option[QueryGenerator[EngineRequirement]]]
    } yield generator
  }

}
