package ru.craftysoft.schemaregistry.util;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.SqlClient;
import io.vertx.mutiny.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.conf.ParamType;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ru.craftysoft.schemaregistry.util.DbLoggerHelper.*;
import static ru.craftysoft.schemaregistry.util.UuidUtils.generateDefaultUuid;

@RequiredArgsConstructor
public class DbClient {

    private final PgPool pgPool;
    private final DSLContext dslContext;

    public <T> Uni<List<T>> executeBatch(Logger log, String point, Collection<Function<DSLContext, Query>> queryBuilders, Function<Row, T> mapper) {
        return executeBatch(pgPool, log, point, queryBuilders, mapper);
    }

    public <T> Uni<List<T>> executeBatch(SqlClient sqlClient, Logger log, String point, Collection<Function<DSLContext, Query>> queryBuilders, Function<Row, T> mapper) {
        var sql = extractSql(queryBuilders.iterator().next());
        var args = queryBuilders.stream()
                .map(this::extractArgs)
                .toList();
        return executeBatch(sqlClient, log, point, sql, args, mapper);
    }

    public <T> Uni<List<T>> executeBatch(Logger log, String point, String sql, List<Tuple> args, Function<Row, T> mapper) {
        return executeBatch(pgPool, log, point, sql, args, mapper);
    }

    public <T> Uni<List<T>> executeBatch(SqlClient sqlClient, Logger log, String point, String sql, List<Tuple> args, Function<Row, T> mapper) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).executeBatch(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .map(rows -> {
                    var resultList = new ArrayList<T>();
                    var rowsSet = rows.value();
                    while (rowsSet != null) {
                        rowsSet.iterator().forEachRemaining(x -> resultList.add(mapper.apply(x)));
                        rowsSet = rowsSet.next();
                    }
                    logOutCount(log, point, queryId, resultList.size());
                    return resultList;
                });
    }

    public Uni<Integer> executeBatch(Logger log, String point, Collection<Function<DSLContext, Query>> queryBuilders) {
        return executeBatch(pgPool, log, point, queryBuilders);
    }

    public Uni<Integer> executeBatch(SqlClient sqlClient, Logger log, String point, Collection<Function<DSLContext, Query>> queryBuilders) {
        var sql = extractSql(queryBuilders.iterator().next());
        var args = queryBuilders.stream()
                .map(this::extractArgs)
                .toList();
        return executeBatch(sqlClient, log, point, sql, args);
    }

    public Uni<Integer> executeBatch(Logger log, String point, String sql, List<Tuple> args) {
        return executeBatch(pgPool, log, point, sql, args);
    }

    public Uni<Integer> executeBatch(SqlClient sqlClient, Logger log, String point, String sql, List<Tuple> args) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).executeBatch(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .map(rows -> {
                    int count = 0;
                    var rowsSet = rows.value();
                    while (rowsSet != null) {
                        count++;
                        rowsSet = rowsSet.next();
                    }
                    logOutCount(log, point, queryId, count);
                    return count;
                });
    }

    public <RECORD extends QualifiedRecord<RECORD>> Uni<Integer> insert(Logger log, String point, RECORD record) {
        return insert(pgPool, log, point, record);
    }

    public <RECORD extends QualifiedRecord<RECORD>> Uni<Integer> insert(SqlClient sqlClient, Logger log, String point, RECORD record) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.insertInto((Table<RECORD>) record.getQualifier())
                .set(record);
        return execute(sqlClient, log, point, queryBuilder);
    }

    public <RECORD extends QualifiedRecord<RECORD>, T> Uni<T> insertWithReturning(Logger log,
                                                                                  String point,
                                                                                  RECORD record,
                                                                                  List<TableField<RECORD, ?>> returningFields,
                                                                                  Function<Row, T> fieldsMapper) {
        return insertWithReturning(pgPool, log, point, record, returningFields, fieldsMapper);
    }

    public <RECORD extends QualifiedRecord<RECORD>, T> Uni<T> insertWithReturning(SqlClient sqlClient,
                                                                                  Logger log,
                                                                                  String point,
                                                                                  RECORD record,
                                                                                  List<TableField<RECORD, ?>> returningFields,
                                                                                  Function<Row, T> mapper) {
        Function<DSLContext, Query> queryBuilder = dslContext -> dslContext.insertInto((Table<RECORD>) record.getQualifier())
                .set(record)
                .returning(returningFields);
        return toUni(sqlClient, log, point, queryBuilder, mapper);
    }

    public Uni<Integer> execute(Logger log, String point, Function<DSLContext, Query> queryBuilder) {
        return execute(pgPool, log, point, queryBuilder);
    }

    public Uni<Integer> execute(SqlClient sqlClient, Logger log, String point, Function<DSLContext, Query> queryBuilder) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return execute(sqlClient, log, point, sql, args);
    }

    public Uni<Integer> execute(Logger log, String point, String sql, Tuple args) {
        return execute(pgPool, log, point, sql, args);
    }

    public Uni<Integer> execute(SqlClient sqlClient, Logger log, String point, String sql, Tuple args) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).execute(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .map(rows -> {
                    logOutCount(log, point, queryId, rows);
                    return rows.rowCount();
                });
    }

    public <T> Multi<T> toMulti(Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        return toMulti(pgPool, log, point, queryBuilder, mapper);
    }

    public <T> Multi<T> toMulti(SqlClient sqlClient, Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return toMulti(sqlClient, log, point, sql, args, mapper);
    }

    public <T> Multi<T> toMulti(Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toMulti(pgPool, log, point, sql, args, mapper);
    }

    public <T> Multi<T> toMulti(SqlClient sqlClient, Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).execute(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .invoke(rows -> {
                    if (log.isDebugEnabled()) {
                        var result = new ArrayList<>();
                        rows.iterator().forEachRemaining(row -> result.add(mapper.apply(row)));
                        logOut(log, point, queryId, rows, result);
                    }
                })
                .toMulti()
                .flatMap(Multi.createFrom()::iterable)
                .map(mapper);
    }

    public <T> Uni<T> toUni(Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        return toUni(pgPool, log, point, queryBuilder, mapper);
    }

    public <T> Uni<T> toUni(SqlClient sqlClient, Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return toUni(sqlClient, log, point, sql, args, mapper);
    }

    public <T> Uni<T> toUni(Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toUni(pgPool, log, point, sql, args, mapper);
    }

    public <T> Uni<T> toUni(SqlClient sqlClient, Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).execute(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        logOut(log, point, queryId, rows, null);
                        return null;
                    }
                    var row = rows.iterator().next();
                    var result = mapper.apply(row);
                    logOut(log, point, queryId, rows, result);
                    return result;
                });
    }

    public <T> Uni<List<T>> toUniOfList(Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        return toUniOfList(pgPool, log, point, queryBuilder, mapper);
    }

    public <T> Uni<List<T>> toUniOfList(SqlClient sqlClient, Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return toUniOfList(sqlClient, log, point, sql, args, mapper);
    }

    public <T> Uni<List<T>> toUniOfList(Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toUniOfList(pgPool, log, point, sql, args, mapper);
    }

    public <T> Uni<List<T>> toUniOfList(SqlClient sqlClient, Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toUniOfCollection(sqlClient, log, point, sql, args, mapper, List::of, ArrayList::new);
    }

    public <T> Uni<Set<T>> toUniOfSet(Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        return toUniOfSet(pgPool, log, point, queryBuilder, mapper);
    }

    public <T> Uni<Set<T>> toUniOfSet(SqlClient sqlClient, Logger log, String point, Function<DSLContext, Query> queryBuilder, Function<Row, T> mapper) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return toUniOfSet(sqlClient, log, point, sql, args, mapper);
    }

    public <T> Uni<Set<T>> toUniOfSet(Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toUniOfSet(pgPool, log, point, sql, args, mapper);
    }

    public <T> Uni<Set<T>> toUniOfSet(SqlClient sqlClient, Logger log, String point, String sql, Tuple args, Function<Row, T> mapper) {
        return toUniOfCollection(sqlClient, log, point, sql, args, mapper, Set::of, HashSet::new);
    }

    public <COLLECTION extends Collection<T>, T> Uni<COLLECTION> toUniOfCollection(SqlClient sqlClient,
                                                                                   Logger log,
                                                                                   String point,
                                                                                   String sql,
                                                                                   Tuple args,
                                                                                   Function<Row, T> mapper,
                                                                                   Supplier<COLLECTION> empty,
                                                                                   Supplier<COLLECTION> initializer) {
        var queryId = generateDefaultUuid();
        logIn(log, point, queryId, sql, args);
        return sqlClient.preparedQuery(sql).execute(args)
                .onFailure().invoke(e -> logError(log, point, queryId, e))
                .map(rows -> {
                    if (rows.rowCount() == 0) {
                        logOut(log, point, queryId, rows, null);
                        return empty.get();
                    }
                    var result = initializer.get();
                    rows.iterator().forEachRemaining(row -> result.add(mapper.apply(row)));
                    logOut(log, point, queryId, rows, result);
                    return result;
                });
    }

    public <COLLECTION extends Collection<T>, T> Uni<COLLECTION> toUniOfCollection(Logger log,
                                                                                   String point,
                                                                                   Function<DSLContext, Query> queryBuilder,
                                                                                   Function<Row, T> mapper,
                                                                                   Supplier<COLLECTION> empty,
                                                                                   Supplier<COLLECTION> initializer) {
        var sql = extractSql(queryBuilder);
        var args = extractArgs(queryBuilder);
        return toUniOfCollection(pgPool, log, point, sql, args, mapper, empty, initializer);
    }

    private String extractSql(Function<DSLContext, Query> queryBuilder) {
        var query = queryBuilder.apply(dslContext);
        var sql = query.getSQL(ParamType.NAMED);
        for (var entry : query.getParams().entrySet()) {
            var key = entry.getKey();
            var dataType = entry.getValue().getDataType();
            if (dataType.isArray()) {
                sql = sql.replace("$" + key + "::" + dataType.getCastTypeName() + "[]", "$" + key);
            }
        }
        return sql;
    }

    private Tuple extractArgs(Function<DSLContext, Query> queryBuilder) {
        var query = queryBuilder.apply(dslContext);
        var parameters = query.getBindValues().stream()
                .map(arg -> {
                    if (arg instanceof JSON json) {
                        return json.data();
                    }
                    if (arg instanceof JSONB jsonb) {
                        return jsonb.data();
                    }
                    if (arg instanceof EnumType enumeration) {
                        return enumeration.getLiteral();
                    }
                    return arg;
                })
                .collect(Collectors.toList());
        return Tuple.tuple(parameters);
    }

}
