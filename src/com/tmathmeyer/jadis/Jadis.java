package com.tmathmeyer.jadis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;

import redis.clients.jedis.Jedis;

import com.google.gson.Gson;
import com.tmathmeyer.jadis.async.CallBackLogger;
import com.tmathmeyer.jadis.async.Promise;

/**
 * 
 * @author tmathmeyer
 *
 * Asynchronous Redis Client!!
 * for now, it only supports the basic lists and maps of redis
 *
 */
public class Jadis
{

    /*
     * that's right, jadis is (for now) a stupid wrapper around jedis
     */
    private final Jedis base;
    private final Gson parser;
    private final Semaphore lock;

    /**
     * Maked a Jadis object by building the connection via provided URL
     * 
     * @param name the server url
     */
    private Jadis(String name)
    {
        base = new Jedis(name);
        parser = new Gson();
        lock = new Semaphore(1);
    }

    /**
     * defualt constructor for a jadis
     * should never be executed more than once
     */
    private Jadis()
    {
        this("localhost");
    }

    /**
     * gets the singleton instance of jadis, to make sure that there are no open conflicting connections to the database
     * 
     * @param name the server name for jadis... pass null or 'localhost' for local
     * @return the the static instance of jadis
     */
    public static Jadis getJadis(String name)
    {
        if (name == null)
        {
            return new Jadis();
        }
        return new Jadis(name);
    }

    /**
     * Get's all the elements in the specified map,
     * equivalent to hgetall in the redis-cli
     * 
     * @param mapName the name of the map to get from redis
     * @param executor the Promise that gets it's getMap() function called when this finished executing
     * @param clazz the type of element that is being parsed
     * @param cbl the logger for exceptions to bring them out of the API
     */
    public <T> void getMap(final String mapName, final Promise<T> executor, final Class<T> clazz, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                Map<String, String> query = getBase().hgetAll(mapName);
                lock.release();
                Map<String, T> parsed = new HashMap<String, T>();
                for(Entry<String, String> e : query.entrySet())
                {
                    try
                    {
                        T t = parser.fromJson(e.getValue(), clazz);
                        parsed.put(e.getKey(), t);
                    }
                    catch (Exception ex)
                    {
                        cbl.Log(ex, clazz);
                    }
                }
                executor.getMap(parsed);
            }
        });
    }

    /**
     * get a named value from the proovided map
     * 
     * @param mapName the name of the map in redis
     * @param elementKey the name of the element in the named map
     * @param executor the Promise to be called into when the query finishes
     * @param clazz
     * @param cbl
     */
    public <T> void getFromMap(final String mapName, final String elementKey, final Promise<T> executor, final Class<T> clazz, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                try 
                {
                    String serial = getBase().hget(mapName, elementKey);
                    executor.getObject(parser.fromJson(serial, clazz), elementKey);
                    lock.release();
                }
                catch (Exception e)
                {
                    lock.release();
                    cbl.Log(e, clazz); 
                }
            }
        });
    }

    /**
     * 
     * @param mapName the name of the map
     * @param elemKey the name of the key corresponding to the value
     * @param insert the value itself
     * @param cbl the callback logger to elevate the exception out of the API
     */
    public <T> void putMap(final String mapName, final String elemKey, final T insert, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                try {
                	String s = parser.toJson(insert);
                    getBase().hset(mapName, elemKey, s);
                    lock.release();
                } catch (Exception e) {
                    cbl.Log(e, insert.getClass());
                    lock.release();
                }
            }
        });
    }
    
    
    /**
     * 
     */
    public <T> void addSet(final String setName, final T insert, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                String s = parser.toJson(insert);
                while(!lock.tryAcquire());
                try
                {
                    getBase().sadd(setName, s);
                    lock.release();
                } catch (Exception e) {
                    cbl.Log(e, insert.getClass());
                    lock.release();
                }
            }
        });
    }
    
    public <T> void remFSet(final String setName, final T delete, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                String s = parser.toJson(delete);
                while(!lock.tryAcquire());
                try
                {
                    getBase().srem(setName, s);
                    lock.release();
                } catch (Exception e) {
                    cbl.Log(e, delete.getClass());
                    lock.release();
                }
            }
        });
    }
    
    public <T> void getASet(final String setName, final Promise<T> executor, final Class<T> clazz, final CallBackLogger<Exception> cbl)
    {
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                try 
                {
                	getBase();
                	Set<String> members = getBase().smembers(setName);
                	Set<T> deserialized = new HashSet<T>();
                	for(String s : members)
                	{
                		T t = parser.fromJson(s, clazz);
                		deserialized.add(t);
                	}
                    executor.getSet(deserialized);
                    lock.release();
                }
                catch (Exception e)
                {
                    lock.release();
                    cbl.Log(e, clazz); 
                }
            }
        });
    }
    
    
    
    
    
    
    
    
    
    
    
    

    /**
     * bulk deleter of items
     * 
     * @param mapName the map name to delete from
     * @param cbl the error handler to elevate the errors
     * @param objNames the names of all the keys to remove from the DB
     */
    public <T> void delMap(final String mapName, final CallBackLogger<Exception> cbl, final String... objNames)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                try
                {
                    getBase().hdel(mapName, objNames);
                    lock.release();
                }
                catch (Exception e)
                {
                    cbl.Log(e, Object.class);
                    lock.release();
                }
            }

        });
    }

    /**
     * 
     * @param listName the name of the list to retrieve
     * @param executor the promise that gets executed when the quuery finishes
     * @param clazz the type of object in the list
     */
    public <T> void getList(final String listName, final Promise<T> executor, final Class<T> clazz)
    {
        exec(new Runnable()
        {
            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                Long size = getBase().llen(listName);
                List<String> query = getBase().lrange(listName, 0, size);
                List<T> parsed = new LinkedList<T>();
                for(String e : query)
                {
                    T t = parser.fromJson(e, clazz);
                    parsed.add(t);
                }
                executor.getList(parsed);
                lock.release();
            }
        });
    }

    /**
     * 
     * @param listName the name of the list
     * @param insert
     */
    public <T> void pushList(final String listName, final T insert)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                getBase().lpush(listName, parser.toJson(insert));
                lock.release();
            }
        });
    }

    /**
     * 
     * @param listName the name of the list
     * @param executor the promise that gets executed when the query finishes
     * @param clazz the type of object in the list
     */
    public <T> void popList(final String listName, final Promise<T> executor, final Class<T> clazz)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                executor.getObject(parser.fromJson(getBase().lpop(listName), clazz),  listName+"-0");
                lock.release();
            }

        });
    }

    /**
     * 
     * @param listName the name of the list
     * @param executor the promise that gets executed wen the query finishes
     * @param clazz the type of object in the list
     */
    public <T> void peekList(final String listName, final Promise<T> executor, final Class<T> clazz)
    {
        exec(new Runnable(){

            @Override
            public void run()
            {
                while(!lock.tryAcquire());
                Long size = getBase().llen(listName);
                executor.getObject(parser.fromJson(getBase().lindex(listName, size-1), clazz), listName+"-"+size);
                lock.release();
            }

        });
    }

    /**
     * Creates a thread, runs it
     * @param r the runnable to execute
     */
    public void exec(Runnable r)
    {
    	if (isAsync)
        {
    		Thread async = new Thread(r);
        	async.start();
        }
    	else
    	{
    		r.run();
    	}
    }
    
    private boolean isAsync = true;
    
    public void setNonAsync()
    {
    	isAsync = false;
    }

    /**
     * single-thread-only... needed for removal of concurrent exceptions
     * @return the Jedis
     */
    private synchronized Jedis getBase()
    {
        return base;
    }
}
