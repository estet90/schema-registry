package ru.craftysoft.schemaregistry.service.dao;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.SqlClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Query;
import ru.craftysoft.schemaregistry.model.jooq.tables.records.VersionsRecord;
import ru.craftysoft.schemaregistry.util.DbClient;

import javax.annotation.Nullable;
import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static ru.craftysoft.schemaregistry.model.jooq.tables.Structures.STRUCTURES;
import static ru.craftysoft.schemaregistry.model.jooq.tables.Versions.VERSIONS;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class VersionDao {

    private final DbClient dbClient;

    public Uni<Long> create(SqlClient sqlClient, VersionsRecord record) {
        return dbClient.insertWithReturning(sqlClient, log, "VersionDao.create", record, List.of(VERSIONS.ID), row -> row.getLong(VERSIONS.ID.getName()));
    }

    public Uni<String> getLink(SqlClient sqlClient, long id) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.select(VERSIONS.LINK)
                .from(VERSIONS)
                .where(VERSIONS.ID.eq(id));
        return dbClient.toUni(sqlClient, log, "VersionDao.getLink", queryBuilder, row -> row.getString(VERSIONS.LINK.getName()));
    }

    public Uni<VersionsRecord> get(SqlClient sqlClient, long structureId, String name) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext
                .select(
                        VERSIONS.ID,
                        VERSIONS.LINK
                )
                .from(VERSIONS)
                .where(
                        VERSIONS.STRUCTURE_ID.eq(structureId),
                        VERSIONS.NAME.eq(name)
                );
        return dbClient.toUni(sqlClient, log, "VersionDao.getLink", queryBuilder, row -> new VersionsRecord(
                row.getLong(VERSIONS.ID.getName()),
                name,
                structureId,
                row.getString(VERSIONS.LINK.getName()),
                null
        ));
    }

    public Uni<Long> deleteAndReturnStructureId(SqlClient sqlClient, long id) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.deleteFrom(VERSIONS)
                .where(VERSIONS.ID.eq(id))
                .returning(VERSIONS.STRUCTURE_ID);
        return dbClient.toUni(sqlClient, log, "VersionDao.delete", queryBuilder, row -> row.getLong(VERSIONS.STRUCTURE_ID.getName()));
    }

    public Uni<Integer> delete(SqlClient sqlClient, long id) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.deleteFrom(VERSIONS)
                .where(VERSIONS.ID.eq(id));
        return dbClient.execute(sqlClient, log, "VersionDao.delete", queryBuilder);
    }

    public Uni<Set<VersionsRecord>> getByStructureId(SqlClient sqlClient, long structureId) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext
                .select(
                        VERSIONS.LINK,
                        VERSIONS.ID
                )
                .from(VERSIONS)
                .where(VERSIONS.STRUCTURE_ID.eq(structureId));
        return dbClient.toUniOfSet(sqlClient, log, "VersionDao.getByStructureId", queryBuilder, row -> new VersionsRecord(
                row.getLong(VERSIONS.ID.getName()),
                null,
                null,
                row.getString(VERSIONS.LINK.getName()),
                null
        ));
    }

    public Uni<Set<VersionsRecord>> getByStructureId(long structureId) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext
                .selectFrom(VERSIONS)
                .where(VERSIONS.STRUCTURE_ID.eq(structureId));
        return dbClient.toUniOfSet(log, "VersionDao.getByStructureId", queryBuilder, row -> new VersionsRecord(
                row.getLong(VERSIONS.ID.getName()),
                row.getString(VERSIONS.NAME.getName()),
                null,
                row.getString(VERSIONS.LINK.getName()),
                row.getOffsetDateTime(VERSIONS.CREATED_AT.getName())
        ));
    }

    public Uni<String> getLink(@Nullable Long structureId,
                               @Nullable String structureName,
                               @Nullable Long versionId,
                               @Nullable String versionName) {
        Function<DSLContext, Query> queryBuilder = dslContext -> resolveGetLinkQuery(dslContext, structureId, structureName, versionId, versionName);
        return dbClient.toUni(log, "VersionDao.getLink", queryBuilder, row -> row.getString(VERSIONS.LINK.getName()));
    }

    private Query resolveGetLinkQuery(DSLContext dslContext,
                                      @Nullable Long structureId,
                                      @Nullable String structureName,
                                      @Nullable Long versionId,
                                      @Nullable String versionName) {
        var query = dslContext.select(VERSIONS.LINK)
                .from(VERSIONS);
        if (versionId != null) {
            return query.where(VERSIONS.ID.eq(versionId));
        }
        if (structureId != null) {
            return query.where(
                    VERSIONS.NAME.eq(versionName),
                    VERSIONS.STRUCTURE_ID.eq(structureId)
            );
        }
        return query.join(STRUCTURES).on(STRUCTURES.ID.eq(VERSIONS.STRUCTURE_ID))
                .where(
                        VERSIONS.NAME.eq(versionName),
                        STRUCTURES.NAME.eq(structureName)
                );
    }

}
