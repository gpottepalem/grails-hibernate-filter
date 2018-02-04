package org.grails.plugin.hibernate.filter

import grails.core.GrailsClass
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory

/**
 * Created by akramer on 12/6/16.
 */
class HibernateFilterConnectionSourceFactory extends HibernateConnectionSourceFactory {

    HibernateFilterBinder filterBinder

    HibernateFilterConnectionSourceFactory(GrailsClass[] grailsDomainclasses) {
        super(*grailsDomainclasses*.clazz)

        this.filterBinder = new HibernateFilterBinder(grailsDomainclasses)
        this.metadataContributor = this.filterBinder
    }
}
