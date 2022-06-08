package ru.craftysoft.schemaregistry.configuration;

import io.vertx.mutiny.pgclient.PgPool;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DefaultDSLContext;
import ru.craftysoft.schemaregistry.util.DbClient;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DbConfiguration {

    @ApplicationScoped
    DbClient dbClient(PgPool pgPool, DSLContext dslContext) {
        return new DbClient(pgPool, dslContext);
    }

    @ApplicationScoped
    DSLContext dslContext() {
        var settings = new Settings()
                .withRenderNamedParamPrefix("$")
                .withRenderFormatted(true);
        return new DefaultDSLContext(SQLDialect.POSTGRES, settings);
    }

}
