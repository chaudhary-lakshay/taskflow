package com.taskflow.support

import com.taskflow.user.UserRepository
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Test-only hook that makes a registration race deterministic. While armed for an email, every
 * UserRepository.existsByEmail(email) call returns its real answer and then waits at a barrier, so
 * N concurrent registrations all finish the pre-check (all see "free") before any of them inserts.
 */
object RaceGate {
    @Volatile private var email: String? = null
    @Volatile private var barrier: CyclicBarrier? = null
    val preChecksPassed = AtomicInteger()
    val sawFree = AtomicInteger()

    fun arm(email: String, parties: Int) {
        preChecksPassed.set(0)
        sawFree.set(0)
        this.barrier = CyclicBarrier(parties)
        this.email = email
    }

    fun disarm() {
        email = null
        barrier = null
    }

    internal fun afterPreCheck(arg: String, result: Boolean) {
        if (arg != email) return
        preChecksPassed.incrementAndGet()
        if (!result) sawFree.incrementAndGet()
        barrier?.await(20, TimeUnit.SECONDS)
    }
}

@TestConfiguration
class RaceGateConfig {
    companion object {
        @Bean
        @JvmStatic
        fun raceGatePostProcessor() = object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                if (bean !is UserRepository) return bean
                return Proxy.newProxyInstance(bean.javaClass.classLoader, arrayOf(UserRepository::class.java)) { _, method, args ->
                    val result = try {
                        method.invoke(bean, *(args ?: emptyArray()))
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                    if (method.name == "existsByEmail") RaceGate.afterPreCheck(args[0] as String, result as Boolean)
                    result
                }
            }
        }
    }
}
