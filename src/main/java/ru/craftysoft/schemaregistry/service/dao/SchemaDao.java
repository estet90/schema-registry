package ru.craftysoft.schemaregistry.service.dao;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.SqlClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Query;
import ru.craftysoft.schemaregistry.model.jooq.tables.records.SchemasRecord;
import ru.craftysoft.schemaregistry.util.DbClient;

import javax.annotation.Nullable;
import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static ru.craftysoft.schemaregistry.model.jooq.tables.Schemas.SCHEMAS;
import static ru.craftysoft.schemaregistry.model.jooq.tables.Structures.STRUCTURES;
import static ru.craftysoft.schemaregistry.model.jooq.tables.Versions.VERSIONS;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class SchemaDao {

    private final DbClient dbClient;

    public Uni<String> getLink(@Nullable Long schemaId,
                               @Nullable String schemaPath,
                               @Nullable String versionName,
                               @Nullable String structureName) {
        Function<DSLContext, Query> queryBuilder = dslContext -> resolveGetLinkQuery(dslContext, schemaId, schemaPath, versionName, structureName);
        return dbClient.toUni(log, "SchemaDao.getLink", queryBuilder, row -> row.getString(SCHEMAS.LINK.getName()));
    }

    private Query resolveGetLinkQuery(DSLContext dslContext,
                                      @Nullable Long schemaId,
                                      @Nullable String schemaPath,
                                      @Nullable String versionName,
                                      @Nullable String structureName) {
        var query = dslContext.select(SCHEMAS.LINK)
                .from(SCHEMAS);
        if (schemaId != null) {
            return query.where(SCHEMAS.ID.eq(schemaId));
        }
        return query
                .join(VERSIONS).on(VERSIONS.ID.eq(SCHEMAS.VERSION_ID).and(VERSIONS.NAME.eq(versionName)))
                .join(STRUCTURES).on(STRUCTURES.ID.eq(VERSIONS.STRUCTURE_ID).and(STRUCTURES.NAME.eq(structureName)))
                .where(SCHEMAS.PATH.eq(schemaPath));
    }

    public Uni<Set<String>> getLinksByVersionId(SqlClient sqlClient, long versionId) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.select(SCHEMAS.LINK)
                .from(SCHEMAS)
                .where(SCHEMAS.VERSION_ID.eq(versionId));
        return dbClient.toUniOfSet(sqlClient, log, "SchemaDao.getLinksByVersionId", queryBuilder, row -> row.getString(SCHEMAS.LINK.getName()));
    }

    public Uni<Set<String>> getLinksByVersionsIds(SqlClient sqlClient, Set<Long> versionsIds) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.select(SCHEMAS.LINK)
                .from(SCHEMAS)
                .where(SCHEMAS.VERSION_ID.in(versionsIds));
        return dbClient.toUniOfSet(sqlClient, log, "SchemaDao.getLinksByVersionsIds", queryBuilder, row -> row.getString(SCHEMAS.LINK.getName()));
    }

    public Uni<List<Long>> create(SqlClient sqlClient, Stream<SchemasRecord> records) {
        var queryBuilders = records
                .map(record -> (Function<DSLContext, Query>) dslContext -> dslContext.insertInto(SCHEMAS)
                        .set(record)
                        .returning(SCHEMAS.ID)
                )
                .toList();
        return dbClient.executeBatch(sqlClient, log, "SchemaDao.create", queryBuilders, row -> row.getLong(SCHEMAS.ID.getName()));
    }

    public Uni<Set<SchemasRecord>> getByVersionsIds(Set<Long> versionsIds) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.selectFrom(SCHEMAS)
                .where(SCHEMAS.VERSION_ID.in(versionsIds));
        return dbClient.toUniOfSet(log, "SchemaDao.getByVersionsIds", queryBuilder, row -> new SchemasRecord(
                row.getLong(SCHEMAS.ID.getName()),
                row.getString(SCHEMAS.PATH.getName()),
                row.getLong(SCHEMAS.VERSION_ID.getName()),
                row.getString(SCHEMAS.LINK.getName())
        ));
    }
}
