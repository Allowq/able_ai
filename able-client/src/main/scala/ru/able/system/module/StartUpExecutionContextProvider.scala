package ru.able.system.module

import java.util.concurrent.ForkJoinPool

import com.google.inject.Provider

import scala.concurrent.ExecutionContext

class StartUpExecutionContextProvider extends Provider[ExecutionContext] {
  override def get(): ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool(1))
}
