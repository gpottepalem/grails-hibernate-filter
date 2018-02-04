package org.grails.plugin.hibernate.filter

import grails.core.GrailsClass
import org.grails.datastore.mapping.model.PersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.mapping.PersistentClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.hibernate.mapping.Collection

/**
 * Add the filters from the domain closure.
 */
class HibernateFilterBuilder {

    private Logger log = LoggerFactory.getLogger(getClass())

    InFlightMetadataCollector mappings
    GrailsClass grailsDomainClass
    PersistentClass persistentClass
    List<FilterDefinition> filterDefinitions = []

    HibernateFilterBuilder(InFlightMetadataCollector mappings, GrailsClass domainClass, PersistentClass persistentClass) {
        log.debug "Building HibernateFilterBuilder for Grails domain class:${domainClass.fullName} persistentClass: ${persistentClass.entityName}"
        this.grailsDomainClass = domainClass
        this.mappings = mappings
        this.persistentClass = persistentClass

        Closure filtersClosure = domainClass.getPropertyValue('hibernateFilters')
        filtersClosure.delegate = this
        filtersClosure.resolveStrategy = Closure.DELEGATE_ONLY
        filtersClosure()
    }

    def methodMissing(String name, args) {
        log.debug "methodMissing for name:$name args:$args"
        args = [name] + args.collect { it }
        def filterMethod = metaClass.getMetaMethod('addFilter', args.collect { it.getClass() } as Object[])
        if (filterMethod) {
            return filterMethod.invoke(this, args as Object[])
        }

        throw new HibernateFilterException(
            "Invalid arguments in hibernateFilters closure [class:$grailsDomainClass.name, name:$name]")
    }

    // Add a previously registered filter
    private void addFilter(String name, Map options = [:]) {
        log.debug "addFilter for name: $name options: $options"
        // Use supplied condition if there is one, otherwise take the condition
        // that is already part of the named filter
        String condition = options.condition ?:
            mappings.filterDefinitions[name].defaultFilterCondition

        // for condition with parameter
        String[] paramTypes = (options.types ?: options.paramTypes ?: '').tokenize(',') as String[]

        // Don't add a filter definition twice - if it is not added already, create the filter
        if (!mappings.getFilterDefinitions().get(name)) {
            def paramsMap = [:]
            int counter = 0
            def matcher = condition =~ /:(\w+)/
            matcher.each { match ->
                String paramName = match[1]
                if (!paramsMap.get(paramName)) {
                    String typeName = paramTypes[counter++].trim()
                    def type = mappings.getTypeResolver().basic(typeName)
                    paramsMap[paramName.trim()] = type
                }
            }
            FilterDefinition filterDefinition = new FilterDefinition(name, condition, paramsMap)
            mappings.addFilterDefinition(filterDefinition)
            //save filter definition for stage-2 of secondPass
            filterDefinitions << filterDefinition
        }
    }

    /**
     * Stage-2 of Second Pass
     * Called from {@link HibernateFilterGrailsPlugin} in doWithApplicationContext after ApplicationContext is fully
     * available.
     */
    void addFilterDefinitionsToPersistentClass(InFlightMetadataCollector mappings, PersistentEntity persistentEntity) {
        filterDefinitions.each {
            addFilterDefinitionToPersistentClass(mappings, persistentEntity, it.filterName, it.defaultFilterCondition, it.parameterTypes)
        }
    }

    void addFilterDefinitionToPersistentClass(InFlightMetadataCollector mappings, PersistentEntity persistentEntity, String name, String condition, Map options) {
        log.debug "Adding filter to persistentEntity: ${persistentEntity.name} persistentClass:${persistentClass.entityName}"
        // If this is a collection, add the filter to the collection,
        // else add the condition to the base class
        def entity = options.collection ?
            mappings.getCollectionBinding("${persistentEntity.name}.$options.collection") :
            persistentClass

        if (entity == null) {
            if (options.collection && !persistentEntity.isRoot()) {
                def clazz = persistentEntity.parentEntity
                log.debug "collection: ${clazz.name}"
                while (clazz != Object && !entity) {
                    entity = mappings.getCollectionBinding("${clazz.name}.$options.collection")
                }
                if (!entity) {
                    log.warn "Collection $options.collection not found in $persistentEntity.name or any superclass"
                    return
                }
            } else {
                log.warn "Entity not found for filter definition $options"
                return
            }
        }

        def filterArgs = [name, condition, true, [:], [:]]
        if (entity instanceof Collection && options.joinTable) {
            entity.addManyToManyFilter(*filterArgs)
        } else {
            log.debug "Adding filter to entity: ${entity.class.name} args:${filterArgs}"
            entity.addFilter(*filterArgs)
        }

        log.debug "Checking options for name: $name options: $options"
        if (options.default) {
            if (options.default instanceof Closure) {
                log.debug "addDefaultFilterCallback in DefaultHibernateFiltersHolder for: ${name}"
                DefaultHibernateFiltersHolder.addDefaultFilterCallback(name, options.default)
            } else {
                log.debug "addDefaultFilter in DefaultHibernateFiltersHolder for: ${name}"
                DefaultHibernateFiltersHolder.addDefaultFilter(name)
            }
        }

        // store any domain alias proxies to be injected later
        if (options.aliasDomain && persistentEntity.isRoot()) {
            log.debug "addDomainAliasProxy in DefaultHibernateFiltersHolder for: ${name}"
            DefaultHibernateFiltersHolder.addDomainAliasProxy(
                new HibernateFilterDomainProxy(persistentEntity.newInstance(), options.aliasDomain, name))
        }
    }
}
