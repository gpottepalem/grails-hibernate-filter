package org.grails.plugin.hibernate.filter

import grails.core.GrailsClass
import groovy.transform.CompileStatic
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.MetadataContributor
import org.jboss.jandex.IndexView

/**
 * Created by akramer on 12/6/16.
 */
@CompileStatic
class HibernateFilterBinder implements MetadataContributor {

    HibernateFilterSecondPass hibernateFilterSecondPass
    GrailsClass[] grailsDomainClasses

    HibernateFilterBinder(GrailsClass[] grailsDomainClasses) {
        this.grailsDomainClasses = grailsDomainClasses
    }

    /**
     * Perform the contributions.
     *
     * @param metadataCollector The metadata collector, representing the in-flight metadata being built
     * @param jandexIndex The Jandex index
     */
    void contribute(InFlightMetadataCollector metadataCollector, IndexView jandexIndex) {
        hibernateFilterSecondPass = new HibernateFilterSecondPass(metadataCollector, grailsDomainClasses)
        metadataCollector.addSecondPass(hibernateFilterSecondPass)
    }
}
