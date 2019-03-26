package com.jd.journalq.nsr.ignite.dao;

import com.jd.journalq.model.PageResult;
import com.jd.journalq.model.Pagination;
import com.jd.journalq.nsr.ignite.model.IgniteBaseModel;
import com.jd.journalq.toolkit.lang.Preconditions;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.processors.cache.CacheEntryImpl;

import java.io.Serializable;
import java.util.*;

/**
 * @author lixiaobin6
 * 下午4:33 2018/7/25
 */
public class IgniteDao implements Serializable {
    private Ignite ignite;
    protected IgniteCache cache;

    public IgniteDao(Ignite ignite, CacheConfiguration cacheConfig) {
        Preconditions.checkArgument(ignite != null, "ignite can not be null.");
        Preconditions.checkArgument(cacheConfig != null, "cache config can not be null.");

        this.ignite = ignite;
        this.cache = this.ignite.getOrCreateCache(cacheConfig);
    }

    public <T extends IgniteBaseModel> void addOrUpdate(T t) {
        cache.put(t.getId(), t);
    }

    public <K> boolean deleteById(K k) {
        return cache.remove(k);
    }

    public <K, T extends IgniteBaseModel> T getById(K k) {
        return (T) cache.get(k);
    }


    public <K, T> List<T> query(SqlQuery<K, T> query) {
        List<T> result = new ArrayList<>();
        List<CacheEntryImpl> list = cache.withKeepBinary().query(query).getAll();
        for (CacheEntryImpl cursor : list) {
            result.add(((BinaryObject) cursor.getValue()).deserialize());
        }
        return result;
    }


    public <K, T> PageResult<T> pageQuery(SqlQuery<K, T> query, Pagination pagination) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setPagination(pagination);

        List<CacheEntryImpl> list = cache.withKeepBinary().query(query).getAll();
        int start = pagination.getStart();
        int pageSize = pagination.getSize();
        int totalRecord = list.size();
        if (totalRecord < start) {
            return pageResult;
        }

        int end = start + pageSize;
        list = list.subList(start, end < totalRecord ? end : totalRecord);

        List<T> data = new ArrayList<>();
        for (CacheEntryImpl cursor : list) {
            data.add(((BinaryObject) cursor.getValue()).deserialize());
        }

        pageResult.setResult(data);
        pagination.setTotalRecord(totalRecord);
        pageResult.setPagination(pagination);

        return pageResult;
    }
    
    public List<Map<String, Object>> query(SqlFieldsQuery query) {
        FieldsQueryCursor<List<?>> queryCursor = cache.query(query);
        List<Map<String, Object>> result = new ArrayList<>();
        int column = queryCursor.getColumnsCount();
        Iterator it = queryCursor.iterator();
        while (it.hasNext()) {
            Map<String, Object> map = new HashMap<>();
            List<?> data = (List<?>) it.next();
            for (int i = 0; i < column; i++) {
                map.put(queryCursor.getFieldName(i), data.get(i));
            }
            result.add(map);
        }
        return result;
    }


    public <T> Collection<T> query(Set<Object> keys) {
        return cache.getAll(keys).values();
    }

    public void remove(Set<Object> keys) {
        cache.removeAll(keys);
    }


    public static class SimpleSqlBuilder {
        private StringBuffer buffer = new StringBuffer("");
        private List args = new ArrayList();
        private Class t;

        public SimpleSqlBuilder(Class t) {
            this.t = t;
        }

        public static SimpleSqlBuilder create(Class t) {
            return new SimpleSqlBuilder(t);
        }

        public SimpleSqlBuilder and(String column, Object value) {
            if (buffer.length() > 0) {
                buffer.append(" and ");
            }

            buffer.append(column).append("=?");
            args.add(value);
            return this;
        }
        public SimpleSqlBuilder in(String column, List list) {
            if (list == null || list.size() <=0)return this;
            if (buffer.length() > 0) {
                buffer.append(" and ");
            }

            buffer.append(column).append(" in (");
            for (int i=0;i< list.size();i++) {
                if(i != list.size()-1 ) {
                    buffer.append("?,");
                    args.add(list.get(i));
                } else {
                    buffer.append("?)");
                    args.add(list.get(i));
                }
            }
            return this;
        }

        public SimpleSqlBuilder or(String column, Object value) {
            if (buffer.length() > 0) {
                buffer.append(" or ");
            }
            buffer.append(column).append("=?");
            args.add(value);
            return this;
        }
        public SimpleSqlBuilder like(String column, Object value) {
            if (buffer.length() > 0) {
                buffer.append(" and ");
            }
            buffer.append(column).append(" like ? ");
            args.add( "%" + value + "%");
            return this;
        }

        public SimpleSqlBuilder order(String column) {
            buffer.append(" order by ?");
            args.add(column);
            return this;
        }

        public SqlQuery build() {
            if (buffer.toString().length() < 1) {
                return new SqlQuery(t, "1=1");
            }
            return new SqlQuery(t, buffer.toString()).setArgs(args.toArray());
        }
    }
}