package ru.able.router.module

import java.util.concurrent.ForkJoinPool

import com.google.inject.Provider

import scala.concurrent.ExecutionContext

class MessageExecutionContextProvider extends Provider[ExecutionContext]{
  override def get(): ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(2))
}
