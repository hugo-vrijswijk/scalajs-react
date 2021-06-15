package japgolly.scalajs.react.util

import japgolly.scalajs.react.userdefined
import japgolly.scalajs.react.util.Util.identityFn
import scala.scalajs.js
import scala.util.Try

object Effect
    extends EffectCallback
       with EffectCatsEffect
       with userdefined.Effects {

  // ===================================================================================================================

  trait Dispatch[F[_]] extends Monad[F] {
    /** Fire and forget, could be sync or async */
    def dispatch[A](fa: F[A]): Unit
    def dispatchFn[A](fa: => F[A]): js.Function0[Unit]
  }

  object Dispatch {
    trait WithDefaults[F[_]] extends Dispatch[F] {
      override def dispatchFn[A](fa: => F[A]): js.Function0[Unit] =
        () => dispatch(fa)
    }
  }

  // ===================================================================================================================

  type Id[A] = A

  trait UnsafeSync[F[_]] extends Monad[F] {

    def runSync[A](fa: => F[A]) : A
    def suspend[A](fa: => F[A]) : F[A]
    def toJsFn [A](fa: => F[A]) : js.Function0[A]

    final def transSync[G[_], A](ga: => G[A])(implicit g: UnsafeSync[G]): F[A] =
      if (this eq g)
        ga.asInstanceOf[F[A]]
      else
        delay(g.runSync(ga))
  }

  object UnsafeSync {

    trait WithDefaults[F[_]] extends UnsafeSync[F] {
      override def toJsFn[A](f: => F[A]): js.Function0[A] =
        () => runSync(f)

      override def suspend[A](fa: => F[A]): F[A] =
        flatMap(delay(fa))(identityFn)
    }

    implicit lazy val id: UnsafeSync[Id] = new WithDefaults[Id] {
      override def delay  [A]   (a: => A)         = a
      override def pure   [A]   (a: A)            = a
      override def map    [A, B](a: A)(f: A => B) = f(a)
      override def flatMap[A, B](a: A)(f: A => B) = f(a)
      override def runSync[A]   (a: => A)         = a
    }
  }

  // ===================================================================================================================

  trait Sync[F[_]] extends UnsafeSync[F] with Dispatch[F] {

    val empty: F[Unit]
    val semigroupSyncOr: Semigroup[F[Boolean]]
    implicit val semigroupSyncUnit: Semigroup[F[Unit]]

    def fromJsFn0   [A]         (f: js.Function0[A])             : F[A]
    def handleError [A, AA >: A](fa: F[A])(f: Throwable => F[AA]): F[AA]
    def isEmpty     [A]         (f: F[A])                        : Boolean
    def reset       [A]         (fa: F[A])                       : F[Unit]
    def runAll                  (callbacks: F[_]*)               : F[Unit]
    def sequence_   [A]         (fas: Iterable[F[A]])            : F[Unit]
    def sequenceList[A]         (fas: List[F[A]])                : F[List[A]]
    def traverse_   [A, B]      (as: Iterable[A])(f: A => F[B])  : F[Unit]
    def traverseList[A, B]      (as: List[A])(f: A => F[B])      : F[List[B]]
    def when_       [A]         (cond: Boolean)(fa: => F[A])     : F[Unit]

    /** Wraps this callback in a `try-finally` block and runs the given callback in the `finally` clause, after the
      * current callback completes, be it in error or success.
      */
    def finallyRun[A, B](fa: F[A], runFinally: F[B]): F[A]

    @inline final def unless_[A](cond: Boolean)(fa: => F[A]): F[Unit] =
      when_(!cond)(fa)
  }

  object Sync {
    type Untyped[A] = js.Function0[A]

    trait WithDefaultDispatch[F[_]] extends Sync[F] with Dispatch.WithDefaults[F] {
      override def dispatch[A](fa: F[A]): Unit =
        if (!isEmpty(fa))
          runSync(fa)
    }

    trait WithDefaults[F[_]] extends WithDefaultDispatch[F] with UnsafeSync.WithDefaults[F] {

      /** Wraps this callback in a `try-finally` block and runs the given callback in the `finally` clause, after the
        * current callback completes, be it in error or success.
        */
      override def finallyRun[A, B](fa: F[A], runFinally: F[B]): F[A] =
        delay { try runSync(fa) finally runSync(runFinally) }

      override def reset[A](fa: F[A]): F[Unit] =
        delay(
          try
            runSync(fa): Unit
          catch {
            case t: Throwable =>
              t.printStackTrace()
          }
        )

      override def runAll(callbacks: F[_]*): F[Unit] =
        callbacks.foldLeft(empty)((x, y) => chain(x, reset(y)))

      override def traverse_[A, B](as: Iterable[A])(f: A => F[B]): F[Unit] =
        delay {
          for (a <- as)
            runSync(f(a))
        }

      override def sequence_[A](fas: Iterable[F[A]]): F[Unit] =
        traverse_(fas)(identityFn)

      override def traverseList[A, B](as: List[A])(f: A => F[B]): F[List[B]] =
        delay {
          val l = List.newBuilder[B]
          for (a <- as)
            l += runSync(f(a))
          l.result()
        }

      override def sequenceList[A](fas: List[F[A]]): F[List[A]] =
        traverseList(fas)(identityFn)

      implicit val semigroupSyncUnit: Semigroup[F[Unit]] =
        Semigroup((f, g) => flatMap(f)(_ => g))

      val semigroupSyncOr: Semigroup[F[Boolean]] =
        Semigroup((f, g) => delay(runSync(f) || runSync(g)))

      override def when_[A](cond: Boolean)(fa: => F[A]): F[Unit] =
        if (cond) map(fa)(_ => ()) else empty

      override def handleError[A, AA >: A](fa: F[A])(f: Throwable => F[AA]): F[AA] =
        delay[AA](
          try
            runSync(fa)
          catch {
            case t: Throwable => runSync(f(t))
          }
        )
    }
  }

  // ===================================================================================================================

  trait Async[F[_]] extends Dispatch[F] {
    def async        [A](f: Async.Untyped[A])  : F[A]
    def first        [A](f: Async.Untyped[A])  : F[A]
    def runAsync     [A](fa: => F[A])          : Async.Untyped[A]
    def toJsPromise  [A](fa: => F[A])          : () => js.Promise[A]
    def fromJsPromise[A](pa: => js.Thenable[A]): F[A]

    /** Wraps this callback in a `try-finally` block and runs the given callback in the `finally` clause, after the
      * current callback completes, be it in error or success.
      */
    def finallyRun[A, B](fa: F[A], runFinally: F[B]): F[A]

    // TODO: FX: Confirm this works. If it does then why does AsyncCallback.viaCallback use a promise?
    final def async_(onCompletion: Sync.Untyped[Unit] => Sync.Untyped[Unit]): F[Unit] =
      async[Unit](f => onCompletion(f(Try(()))))

    final def transAsync[G[_], A](ga: => G[A])(implicit g: Async[G]): F[A] =
      if (this eq g)
        ga.asInstanceOf[F[A]]
      else
        async(g.runAsync(ga)(_))
  }

  object Async {
    type Untyped[A] = (Try[A] => Sync.Untyped[Unit]) => Sync.Untyped[Unit]

    trait WithDefaults[F[_]] extends Async[F] with Dispatch.WithDefaults[F] {
      override def first[A](f: Async.Untyped[A]): F[A] =
        async(g => () => {
          var first = true
          f(ea => () => {
            if (first) {
              first = false
              g(ea).apply()
            }
          }).apply()
        })

      override def dispatch[A](fa: F[A]): Unit =
        toJsPromise(fa).apply(): Unit
    }
  }

}

// =====================================================================================================================

trait EffectCallback
trait EffectCatsEffect
