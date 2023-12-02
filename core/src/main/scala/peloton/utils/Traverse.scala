package peloton.utils

import cats.{Applicative, Parallel}
import cats.implicits.*

extension (r: scala.collection.immutable.Range)
  def traverse_[F[_]: Applicative, B](f: Int => F[B]): F[Unit] = 
    r.asInstanceOf[Seq[Int]].traverse_(f)

  def traverse[F[_]: Applicative, B](f: Int => F[B]): F[Seq[B]] = 
    r.asInstanceOf[Seq[Int]].traverse(f)

  def parTraverse_[F[_]: Applicative: Parallel, B](f: Int => F[B]): F[Unit] = 
    r.asInstanceOf[Seq[Int]].parTraverse_(f)

  def parTraverse[F[_]: Applicative: Parallel, B](f: Int => F[B]): F[Seq[B]] = 
    r.asInstanceOf[Seq[Int]].parTraverse(f)


extension [A] (o: Option[A])
  def traverse[F[_]: Applicative, B](fa: A => F[B]): F[Option[B]] = 
    o match
      case Some(a) => fa(a).map(Some(_))
      case None    => None.pure

  def traverse_[F[_]: Applicative, B](fa: A => F[B]): F[Unit] = 
    o match
      case Some(a) => fa(a).void
      case None    => ().pure

  def parTraverse[F[_]: Applicative, B](fa: A => F[B]): F[Option[B]] = traverse(fa)
  def parTraverse_[F[_]: Applicative, B](fa: A => F[B]): F[Unit] = traverse_(fa)
