/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.datasources.runtime;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.wildfly.swarm.config.datasources.DataSource;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.spi.api.Customizer;
import org.wildfly.swarm.spi.api.annotations.Configurable;
import org.wildfly.swarm.spi.runtime.annotations.Pre;

/** Customizer to attempt auto-detection of JDBC drivers and creation of default datasource, if required.
 *
 * @author Bob McWhirter
 */
@Pre
@Singleton
public class DatasourceAndDriverCustomizer implements Customizer {

    @Inject
    @Any
    Instance<DriverInfo> allDrivers;

    @Inject
    DatasourcesFraction fraction;

    private String defaultDatasourceName;

    @Configurable("swarm.ds.name")
    private String datasourceName = "ExampleDS";

    @Configurable("swarm.ds.connection.url")
    private String datasourceConnectionUrl;

    @Configurable("swarm.ds.username")
    private String datasourceUserName;

    @Configurable("swarm.ds.password")
    private String datasourcePassword;

    @Configurable("swarm.jdbc.driver")
    private String driverName;

    @Override
    public void customize() {
        customizeJDBCDrivers();
        customizeDefaultDatasource();
    }

    protected void customizeJDBCDrivers() {
        this.allDrivers.forEach(this::attemptInstallation);
    }

    protected void customizeDefaultDatasource() {
        List<DataSource> datasources = this.fraction.subresources().dataSources();

        if (datasources.isEmpty()) {
            this.defaultDatasourceName = createDefaultDatasource();
        } else {
            this.defaultDatasourceName = datasources.get(0).getKey();
        }
    }

    private String createDefaultDatasource() {

        if ( this.fraction.subresources().dataSource( this.datasourceName ) != null ) {
            DatasourcesMessages.MESSAGES.notCreatingDatasourceAlreadyExists( this.datasourceName );
            return this.datasourceName;
        }

        Optional<DriverInfo> driverForDefaultDS = Optional.empty();

        if ( this.driverName != null ) {
            driverForDefaultDS = StreamSupport.stream(this.allDrivers.spliterator(), false)
                    .filter(e -> e.name().equals(this.driverName))
                    .findFirst();
        } else {
            List<DriverInfo> installedDrivers = StreamSupport.stream(this.allDrivers.spliterator(), false)
                    .filter(DriverInfo::isInstalled)
                    .collect(Collectors.toList());

            if ( installedDrivers.size() == 1 ) {
                driverForDefaultDS = Optional.of( installedDrivers.get(0));
            } else if ( installedDrivers.size() > 1 ) {
                List<String> driverNames = installedDrivers.stream().map(e->e.name()).collect(Collectors.toList());

                DatasourcesMessages.MESSAGES.notCreatingDatasourceAmbiguousDrivers( String.join( ",", driverNames ) );
                return null;
            }
        }

        if (!driverForDefaultDS.isPresent()) {
            DatasourcesMessages.MESSAGES.notCreatingDatasourceMissingDriver();
            return null;
        }

        driverForDefaultDS.get().installDatasource(this.fraction, this.datasourceName, (ds)->{
            if ( this.datasourceConnectionUrl != null ) {
                ds.connectionUrl( this.datasourceConnectionUrl );
            }

            if ( this.datasourceUserName != null ) {
                ds.userName( this.datasourceUserName );
            }

            if ( this.datasourcePassword != null ) {
                ds.password( this.datasourcePassword );
            }
        });

        return this.datasourceName;
    }

    protected void attemptInstallation(DriverInfo info) {
        if (info.detect(this.fraction)) {
            DatasourcesMessages.MESSAGES.autodetectedJdbcDriver(info.name());
        }
    }

    @Produces
    @Dependent
    @DefaultDatasource
    public String getDatasourceName() {
        return this.defaultDatasourceName;
    }

}
