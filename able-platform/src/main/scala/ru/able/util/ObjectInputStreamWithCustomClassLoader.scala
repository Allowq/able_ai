package ru.able.util

import java.io.{InputStream, ObjectInputStream, ObjectStreamClass}

class ObjectInputStreamWithCustomClassLoader(InputStream: InputStream)
  extends ObjectInputStream(InputStream)
{
  override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
    try {
      Class.forName(desc.getName, false, getClass.getClassLoader)
    } catch {
      case _: ClassNotFoundException => super.resolveClass(
        ObjectStreamClass.lookup(Class.forName("ru.able.server.camera." + desc.getName.split('.').last))
      )
    }
  }
}
