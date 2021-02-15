package ru.able.util

import java.io.{InputStream, ObjectInputStream, ObjectStreamClass}

class ObjectInputStreamWithCustomClassLoader(InputStream: InputStream)
  extends ObjectInputStream(InputStream)
{
  //TODO: you have to make it with more care
  override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
    try {
      Class.forName(desc.getName, false, getClass.getClassLoader)
    } catch {
      case _: ClassNotFoundException => super.resolveClass(
        ObjectStreamClass.lookup(Class.forName("ru.able.server.model." + desc.getName.split('.').last))
      )
    }
  }
}
