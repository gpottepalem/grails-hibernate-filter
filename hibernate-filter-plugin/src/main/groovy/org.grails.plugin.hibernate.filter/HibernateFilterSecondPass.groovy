package org.grails.plugin.hibernate.filter

import grails.core.GrailsClass
import org.hibernate.MappingException
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.cfg.SecondPass

class HibernateFilterSecondPass implements SecondPass {

    InFlightMetadataCollector mappings
    Map persistentClasses

    List<HibernateFilterBuilder> filtersBuilders = []
    GrailsClass[] grailsDomainClasses

    HibernateFilterSecondPass(InFlightMetadataCollector mappings, GrailsClass[] grailsDomainClasses) {
        this.mappings = mappings
        this.grailsDomainClasses = grailsDomainClasses
    }

	void doSecondPass(Map persistentClasses) throws MappingException {
        grailsDomainClasses.each { GrailsClass grailsDomainClass ->
            if(grailsDomainClass.hasProperty('hibernateFilters')) {
                filtersBuilders << new HibernateFilterBuilder(this.mappings, grailsDomainClass, persistentClasses.get(grailsDomainClass.fullName))
            }
        }
        this.persistentClasses = persistentClasses
	}
}
