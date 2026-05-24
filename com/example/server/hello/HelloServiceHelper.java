package com.example.server.hello;


/**
* com/example/server/hello/HelloServiceHelper.java .
* IDL-to-Java コンパイラ (ポータブル), バージョン "5.0" で生成
* 生成元: ./idl-local/hello.idl
* 2026年5月20日水曜日 0時28分53秒 日本標準時
*/

abstract public class HelloServiceHelper
{
  private static String  _id = "IDL:com.example.server/com/example/server/hello/HelloService:1.0";

  public static void insert (org.omg.CORBA.Any a, com.example.server.hello.HelloService that)
  {
    org.omg.CORBA.portable.OutputStream out = a.create_output_stream ();
    a.type (type ());
    write (out, that);
    a.read_value (out.create_input_stream (), type ());
  }

  public static com.example.server.hello.HelloService extract (org.omg.CORBA.Any a)
  {
    return read (a.create_input_stream ());
  }

  private static org.omg.CORBA.TypeCode __typeCode = null;
  synchronized public static org.omg.CORBA.TypeCode type ()
  {
    if (__typeCode == null)
    {
      __typeCode = org.omg.CORBA.ORB.init ().create_interface_tc (com.example.server.hello.HelloServiceHelper.id (), "HelloService");
    }
    return __typeCode;
  }

  public static String id ()
  {
    return _id;
  }

  public static com.example.server.hello.HelloService read (org.omg.CORBA.portable.InputStream istream)
  {
    return narrow (istream.read_Object (_HelloServiceStub.class));
  }

  public static void write (org.omg.CORBA.portable.OutputStream ostream, com.example.server.hello.HelloService value)
  {
    ostream.write_Object ((org.omg.CORBA.Object) value);
  }

  public static com.example.server.hello.HelloService narrow (org.omg.CORBA.Object obj)
  {
    if (obj == null)
      return null;
    else if (obj instanceof com.example.server.hello.HelloService)
      return (com.example.server.hello.HelloService)obj;
    else if (!obj._is_a (id ()))
      throw new org.omg.CORBA.BAD_PARAM ();
    else
    {
      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();
      com.example.server.hello._HelloServiceStub stub = new com.example.server.hello._HelloServiceStub ();
      stub._set_delegate(delegate);
      return stub;
    }
  }

  public static com.example.server.hello.HelloService unchecked_narrow (org.omg.CORBA.Object obj)
  {
    if (obj == null)
      return null;
    else if (obj instanceof com.example.server.hello.HelloService)
      return (com.example.server.hello.HelloService)obj;
    else
    {
      org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl)obj)._get_delegate ();
      com.example.server.hello._HelloServiceStub stub = new com.example.server.hello._HelloServiceStub ();
      stub._set_delegate(delegate);
      return stub;
    }
  }

}
