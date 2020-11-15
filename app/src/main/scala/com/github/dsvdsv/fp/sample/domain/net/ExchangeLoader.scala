package com.github.dsvdsv.fp.sample.domain.net

import cats.effect.Sync
import cats.mtl.Raise
import cats.syntax.all._
import cats.tagless.{autoFunctorK, finalAlg}
import org.http4s.Method.GET
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{Status, Uri}

@finalAlg
@autoFunctorK
trait ExchangeLoader[F[_]] {
  def fetchRates(uri: Uri): F[RateList]
}

object ExchangeLoader {
  def fromClient[F[_]](
      client: Client[F]
  )(implicit F: Sync[F], R: Raise[F, NetworkError]): F[ExchangeLoader[F]] = {
    val dsl = new Http4sClientDsl[F] {}

    import dsl._

    F.delay(
      new ExchangeLoader[F] {
        override def fetchRates(uri: Uri): F[RateList] =
          GET(uri)
            .flatMap(
              client
                .run(_)
                .use(
                  resp =>
                    resp.status match {
                      case Status.Ok =>
                        resp
                          .attemptAs[RateList]
                          .foldF(e => R.raise(NetworkError.DecodeError(e.getMessage())), _.pure[F])
                      case Status.NotFound =>
                        R.raise(NetworkError.NotFound)
                      case _ =>
                        R.raise(NetworkError.ServerError)
                    }
                )
            )
      }
    )
  }
}