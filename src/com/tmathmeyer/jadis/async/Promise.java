package com.tmathmeyer.jadis.async;

import java.util.List;
import java.util.Map;

public interface Promise<T>
{
    public void getMap(Map<String, T> map);

    public void getList(List<T> list);

    public void getObject(T t, String key);
}
