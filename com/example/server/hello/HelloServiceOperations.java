package com.example.server.hello;


/**
* com/example/server/hello/HelloServiceOperations.java .
* IDL-to-Java コンパイラ (ポータブル), バージョン "5.0" で生成
* 生成元: ./idl-local/hello.idl
* 2026年5月20日水曜日 0時28分53秒 日本標準時
*/

public interface HelloServiceOperations 
{
  String sayHello ();
  String sayGoodbye ();
  int add (int a, int b);
  org.omg.CORBA.Any echo (org.omg.CORBA.Any value);
  org.omg.CORBA.Any addAny (org.omg.CORBA.Any a, org.omg.CORBA.Any b);
} // interface HelloServiceOperations
