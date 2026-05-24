package com.example.server.hello;

/**
* com/example/server/hello/HelloServiceHolder.java .
* IDL-to-Java コンパイラ (ポータブル), バージョン "5.0" で生成
* 生成元: ./idl-local/hello.idl
* 2026年5月20日水曜日 0時28分53秒 日本標準時
*/

public final class HelloServiceHolder implements org.omg.CORBA.portable.Streamable
{
  public com.example.server.hello.HelloService value = null;

  public HelloServiceHolder ()
  {
  }

  public HelloServiceHolder (com.example.server.hello.HelloService initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = com.example.server.hello.HelloServiceHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    com.example.server.hello.HelloServiceHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return com.example.server.hello.HelloServiceHelper.type ();
  }

}
