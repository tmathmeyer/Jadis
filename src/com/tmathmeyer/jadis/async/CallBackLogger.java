package com.tmathmeyer.jadis.async;

public interface CallBackLogger<T>
{
    public void Log(T t, Class<?> errorClass);
}
