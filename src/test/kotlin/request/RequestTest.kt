package prod.prog.request

import io.github.vjames19.futures.jdk8.Future
import io.github.vjames19.futures.jdk8.recoverWith
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import prod.prog.actionProperties.Context
import prod.prog.request.resultHandler.ErrorHandler
import prod.prog.request.resultHandler.ResultHandler
import prod.prog.request.source.Source
import prod.prog.request.transformer.Transformer
import prod.prog.service.supervisor.Supervisor
import prod.prog.service.supervisor.solver.Solver
import prod.prog.service.supervisor.solver.requestSolver.RequestContextSolver
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

//@ExtendWith(MockKExtension::class)
class RequestTest {
    private val before = mockk<Solver<Context>>()
    private val after = mockk<Solver<Context>>()
    private val initContext = mockk<RequestContextSolver>()
    private val supervisor = mockk<Supervisor>()

    private val source = mockk<Source<Int>>()
    private val transformer = mockk<Transformer<Int, Int>>()
    private val resultHandler = mockk<ResultHandler<Int>>()
    private val errorHandler = mockk<ErrorHandler>()
    private val context = RequestContext()
    private val request = Request(source, transformer, resultHandler, errorHandler, context)

    private val init = 0
    private val correct = 1
    private val wrong = 2
    private val error = Error("ERROR")

    @BeforeTest
    fun before() {
        every { supervisor.before } returns before
        every { supervisor.after } returns after
        every { supervisor.getInitContext() } returns initContext

        justRun { source.addContext(any()) }
        justRun { transformer.addContext(any()) }
        justRun { resultHandler.addContext(any()) }
        justRun { errorHandler.addContext(any()) }

        every { source.getContext(any()) } returns context.sourceContext
        every { transformer.getContext(any()) } returns context.transformerContext
        every { resultHandler.getContext(any()) } returns context.resultHandlerContext
        every { errorHandler.getContext(any()) } returns context.errorHandlerContext

        every { initContext(any()) } returns context
        every { before(context.sourceContext) } returns context.sourceContext
        every { before(context.transformerContext) } returns context.transformerContext
        every { before(context.resultHandlerContext) } returns context.resultHandlerContext
        every { before(context.errorHandlerContext) } returns context.errorHandlerContext

        every { after(context.sourceContext) } returns context.sourceContext
        every { after(context.transformerContext) } returns context.transformerContext
        every { after(context.resultHandlerContext) } returns context.resultHandlerContext
        every { after(context.errorHandlerContext) } returns context.errorHandlerContext

        every { source.invoke() } returns Future { init }
        every { transformer.invoke(any()) } returns correct
        every { resultHandler.invoke(any()) } returns Unit
        every { errorHandler.invoke(any()) } returns Unit
    }

    @Test
    fun `validate run returns correct result in success case`() {
        val result = request.run(supervisor).recoverWith { Future { wrong } }.get()

        assertEquals(correct, result)
    }

    @Test
    fun `validate run returns correct result on error in source`() {
        every { source.invoke() } returns Future { throw error }

        val result = request.run(supervisor).recoverWith { Future { wrong } }.get()

        assertEquals(wrong, result)
    }

    @Test
    fun `validate run returns correct result on error in transformer`() {
        every { transformer.invoke(any()) } throws error

        val result = request.run(supervisor).recoverWith { Future { wrong } }.get()

        assertEquals(wrong, result)
    }

    @Test
    fun `validate run returns correct result on error in result handler`() {
        every { resultHandler.invoke(any()) } throws error

        val result = request.run(supervisor).recoverWith { Future { wrong } }.get()

        assertEquals(correct, result)
    }

    @Test
    fun `validate run returns correct result on error in error handler`() {
        every { errorHandler.invoke(any()) } throws error

        val result = request.run(supervisor).recoverWith { Future { wrong } }.get()

        assertEquals(correct, result)
    }

    @Test
    fun `validate run uses expected order in success case`() {
        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            after(context.sourceContext)
            before(context.transformerContext)
            after(context.transformerContext)
            before(context.resultHandlerContext)
            after(context.resultHandlerContext)
        }
    }

    @Test
    fun `validate run uses expected order with error in source`() {
        every { source.invoke() } returns Future { throw error }

        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            before(context.errorHandlerContext)
            after(context.errorHandlerContext)
        }
    }

    @Test
    fun `validate run uses expected order with error in transformer`() {
        every { transformer.invoke(any()) } throws error

        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            after(context.sourceContext)
            before(context.transformerContext)
            before(context.errorHandlerContext)
            after(context.errorHandlerContext)
        }
    }

    @Test
    fun `validate run uses expected order with error in result handling`() {
        every { resultHandler.invoke(any()) } throws error

        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            after(context.sourceContext)
            before(context.transformerContext)
            after(context.transformerContext)
            before(context.resultHandlerContext)
            before(context.errorHandlerContext)
            after(context.errorHandlerContext)
        }
    }

    @Test
    fun `validate run uses expected order with error in error handling`() {
        every { source.invoke() } returns Future { throw error }
        every { errorHandler.invoke(any()) } throws error

        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            before(context.errorHandlerContext)
        }
    }

    @Test
    fun `validate run uses expected order with error in result handling and in error handling`() {
        every { resultHandler.invoke(any()) } throws error
        every { errorHandler.invoke(any()) } throws error

        request.run(supervisor)

        verifySequence {
            initContext(any())
            before(context.sourceContext)
            after(context.sourceContext)
            before(context.transformerContext)
            after(context.transformerContext)
            before(context.resultHandlerContext)
            before(context.errorHandlerContext)
        }
    }
}