/**
 * Copyright (c) 2015 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ed.server.pubsub

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, SourceQueueWithComplete}
import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.EdHttp._
import debiki._
import ed.server.{EdContext, EdController}
import ed.server.http._
import ed.server.security.SidAbsent
import javax.inject.Inject
import play.{api => p}
import p.libs.json.{JsString, JsValue, Json}
import p.mvc.{Action, ControllerComponents, RequestHeader, Result}
import scala.concurrent.Future
import scala.util.{Failure, Success}


/** Authorizes and subscribes a user to pubsub messages.
  */
class SubscriberController @Inject()(cc: ControllerComponents, tyCtx: EdContext)
  extends EdController(cc, tyCtx) {

  import context.globals

  def webSocket: p.mvc.WebSocket = p.mvc.WebSocket.acceptOrResult[JsValue, JsValue] {
        request: RequestHeader =>
    webSocketImpl(request)
  }


  def webSocketImpl(request: RequestHeader)
        : Future[Either[
            // Either an error response, if we reject the connection.
            Result,
            // Or an In and Out stream, for talking with the client.
            Flow[JsValue, JsValue, _]]] = {
    import tyCtx.security
    import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

    // !!!
    //  https://stackoverflow.com/questions/30964824/how-to-create-a-source-that-can-receive-elements-later-via-a-method-call
    // How to create a Source that can receive elements later via a method call?


    // Try to avoid actors:
    //   Accessing the underlying ActorRef of an akka stream Source created by Source.actorRef
    // https://stackoverflow.com/questions/30785011/accessing-the-underlying-actorref-of-an-akka-stream-source-created-by-source-act

    // Easy w Play 2.5, hard w >= 2.6:
    //   https://stackoverflow.com/questions/53486314/create-websocket-with-play-framework-2-6

    // https://stackoverflow.com/questions/30964824/how-to-create-a-source-that-can-receive-elements-later-via-a-method-call

    // https://stackoverflow.com/a/47896071/694469
    //   Flow[Int].alsoTo(Sink.foreach(println(_))).to(Sink.ignore)

    // Akka flow sth:
    //   https://doc.akka.io/api/akka/current/akka/stream/javadsl/Flow.html#alsoTo(that:akka.stream.Graph[akka.stream.SinkShape[Out],_]):akka.stream.javadsl.Flow[In,Out,Mat]

    // My Q:
    //  https://stackoverflow.com/questions/61337516/how-use-play-framework-2-8-websocket-with-sink-and-source

    val onCompleteSink = Sink.onComplete(okOrFail =>
      println(s"WS connection gone, ok: ${okOrFail.isSuccess}"))

    //val inSink2: Sink[JsValue, _] = Sink.combine(foreachSink, onCompleteSink)(strategy = ???)
      // strategy is of type:  Int => Graph[UniformFanOutShape[T, U], NotUsed]
      // what does that mean? I suppose I'll try 'alsoTo' instead.

    // Play 2.8 docs w toy example:  https://www.playframework.com/documentation/2.8.x/ScalaWebSockets

    val outSource: Source[JsValue, SourceQueueWithComplete[JsValue]] = Source
      .queue[JsValue](bufferSize = 50, OverflowStrategy.fail)

    val queue2222: SourceQueueWithComplete[JsValue] = Source
      .queue[JsValue](bufferSize = 1234, OverflowStrategy.backpressure)
      //.throttle(elementsToProcess = 1334, 3.second)
      //.map(x => x * x)
      .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)
      .run()(???)
      // No?
      //  https://github.com/playframework/play-samples/tree/2.8.x/play-scala-chatroom-example/

    // what
    //   Access to websocket server materialized value #1167
    // https://github.com/akka/akka-http/issues/1167

    // Unresolved:
    //  " Graph materialized value of websocket upgrade "
    //  https://groups.google.com/forum/#!topic/akka-user/p4HDrgmu6fo

    // So it's undouented:
    //  https://github.com/akka/akka/issues/28794
    //  "Docs: describe how to extract materialized values when `run()` is called elsewhere #28794"


    queue2222.offer(???)

    val foreachSink = Sink.foreach[JsValue](v => {
      //outSource.run()(tyCtx.akkaStreamMaterializer)
      //outSource
      println(v)
    })

    //val inSink: Sink[JsValue, _] = Flow[JsValue].alsoTo(onCompleteSink).to(foreachSink)
    val inSink: Sink[JsValue, _] = foreachSink
      //.throttle(elementsToProcess, 3.second)
      //.map(x => x * x)
      //.toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)
      //.run()

    // I cannot figure out how to send to the queue?  It has no offer() method
    // unless I  run()  it
    // but I think I shouldn't do that — Play or Akka will start it themselves?


    /*
    val queue: Source = Source
      .queue[Int](bufferSize = 100, akka.stream.OverflowStrategy.backpressure)
      // .throttle(elementsToProcess, 3.second)
      //.map(x => x * x)
      .toMat(Sink.foreach { x =>
        println(s"completed $x")
    })(Keep.left)
      // .run()(tyCtx.akkaStreamMaterializer)
    */

    Future.successful(Right(
      Flow.fromSinkAndSourceMat(inSink,  outSource)((_, outboundMat) => {
        outboundMat.offer(JsString("zorro"))
          //browserConnections ::= outboundMat.offer
          //NotUsed
        })
        .watchTermination() { (_, fut) =>    // https://stackoverflow.com/a/54137778/694469
          fut onComplete {
            case Success(_) =>
              println("Client disconnected")
            case Failure(t) =>
              println(s"Disconnection failure: ${t.getMessage}")
          }
        }
      /*/akka.stream.ActorFlo.actorRef { out: akka.actor.ActorRef =>
        val queue = Source
          .queue[Int](bufferSize = 100, akka.stream.OverflowStrategy.backpressure)
          // .throttle(elementsToProcess, 3.second)
          //.map(x => x * x)
          .toMat(Sink.foreach { x =>
            println(s"completed $x")
            out ! x
          })(Keep.left)
          .run()(tyCtx.akkaStreamMaterializer)
          */
    (tyCtx.globals.actorSystem, tyCtx.akkaStreamMaterializer)

    ))
/*

    // A bit dupl code — the same as for normal HTTP requests. [WSHTTPREQ]
    val site = globals.lookupSiteOrThrow(request)
    val dao = globals.siteDao(site.id)
    val expireIdleAfterMins = dao.getWholeSiteSettings().expireIdleAfterMins

    // Eh, hmm, this won't work — there's no xsrf token header.
    // Use the WebSocket's "protocol" thing, for xsrf token?
    val (actualSidStatus, xsrfOk, newCookies) =
      security.checkSidAndXsrfToken(   // throws things — want that?
        request, anyRequestBody = None, siteId = site.id,
              expireIdleAfterMins = expireIdleAfterMins, maySetCookies = false)

    // """the Origin header in the request must be checked against the server’s origin,
    // and manual authentication (including CSRF tokens) should be implemented.
    // If a WebSocket request does not pass the security checks, then
    // acceptOrResult should reject the request by returning a Forbidden result
    //
    // see: https://github.com/playframework/play-samples/blob/75fcbffbb19240b818707127ca7559962368b8d1/play-scala-websocket-example/app/controllers/HomeController.scala#L81
    // — but it's buggy.

    val (mendedSidStatus, deleteSidCookie) =
      if (actualSidStatus.canUse) (actualSidStatus, false)
      else (SidAbsent, true)

    val anyBrowserId = security.getAnyBrowserId(request)
    if (anyBrowserId.isEmpty)
      return Future.successful(Left(
          ForbiddenResult("TyEWS0BRID", "No browser id")))


    dao.perhapsBlockRequest(request, mendedSidStatus, anyBrowserId)
    val anyRequester: Option[Participant] = dao.getUserBySessionId(mendedSidStatus)

    val requesterMaybeSuspended: Participant = anyRequester getOrElse {
      return Future.successful(Left(
        ForbiddenResult("TyEWS0USR", "Not logged in")))
    }

    if (requesterMaybeSuspended.isDeleted)
      return Future.successful(Left(
        ForbiddenResult("TyEWSUSRDLD", "User account deleted")
          .discardingCookies(security.DiscardingSessionCookie)))  // + discard browser id co too

    val isSuspended = requesterMaybeSuspended.isSuspendedAt(new java.util.Date)
    if (isSuspended)
      return Future.successful(Left(
        ForbiddenResult("TyESUSPENDED_", "Your account has been suspended")
          .discardingCookies(security.DiscardingSessionCookie)))

    val requester = requesterMaybeSuspended

    // A bit dupl code, see DebikiRequest [WSHTTPREQ]
    val ip: IpAddress = security.realOrFakeIpOf(request)
    val browserIdData = BrowserIdData(ip = ip, idCookie = anyBrowserId.map(_.cookieValue),
      fingerprint = 0) // skip for now


    // Hi Play Fmw people,
    // When using Sink and Source, how do I know when a WebSocket connection has
    // closed? (e.g. by the client) —  And how do I close a connection?
    // From the docs:
    // Log events to the console
    val in = akka.stream.scaladsl.Sink.foreach[String](println)

    // Send a single 'Hello!' message and then leave the socket open
    val out = akka.stream.scaladsl.Source.single("Hello!")
      .concat(akka.stream.scaladsl.Source.maybe)

    // Leaves me wondering how to close the 'in' and 'out', and how I can know
    // if the client maybe closed them already?
    // Actually I didn't this far find out how to send messages to the 'out'
    // — how do I do that? Looking at the Source docs, I see only
    // methods like  Source.single  or Source.repeat  or  Source.failed,
    // all of which seem off-topic for my use case — I didn't find anything
    // like a function I can call whenever there's a new WebSocket message
    // to send to the browser?
    // How do I send a message to 'out'? (so it gets WebSocket-sent to the browser)

    // How do I send messages to a WebSocket actor I've created?
    // Below, I first have an Actor, and then I have a "Prop".
    // But how do I send messages to any of those?
    // Since one doesn't send messages directly to an Actor —
    // one sends the messages to an ActorRef instead.
    // Maybe what I'm wondering, is, if I have a Prop and an Actor,
    // how do I get an ActorRef?
    //
    // There's this function:
    //
    //    actorSystem.actorOf(theProps, name)
    //
    // but the docs says this creates a *new* actor. However I have one actor
    // already, I don't need to create another one. What do I do?
    //


    Flow.fromSinkAndSource(in, out)

    // If the user is subscribed already, the PubSub actor will delete the old
    // WebSocket connection and use this new one instead. [ONEWSCON]
    Future.successful(Right(
      p.libs.streams.ActorFlow.actorRef { out: akka.actor.ActorRef =>
        val actor = new WebSocketActor(out, site.id, requester.id)
        val requestersActorProps = akka.actor.Props(actor)

        requestersActorProps
      }(tyCtx.globals.actorSystem, tyCtx.akkaStreamMaterializer)))
      */
  }

  private class WebSocketActor(
    webSocketOut: akka.actor.ActorRef,
    siteId: SiteId,
    userId: UserId,
  ) extends akka.actor.Actor {

    def logger: p.Logger = talkyard.server.TyLogger("WebSocketActor")

    override def preStart() {
      val dao = globals.siteDao(siteId)
      val user = dao.getUser(userId) getOrElse {
        logger.warn(s"Actor's user gone")
        // How delete this actor? Maybe a PoisonPill and then clean up in postStop()?
        self ! akka.actor.PoisonPill
        return
      }

      val watchbar: BareWatchbar = dao.getOrCreateWatchbar(userId)
      globals.pubSub.userSubscribed(siteId, user, browserIdData,
        watchbar.watchedPageIds, self)
    }

    override def postStop(): Unit = ()

    def receive: PartialFunction[Any, Unit] = {
      case msg: String =>
        webSocketOut ! ("I received your message: " + msg)
    }
  }

  /*
  let ws = new WebSocket('ws://site-3.localhost/-/websockets');
    ws.onmessage = function(event) {
      const message = JSON.parse(event.data)
      console.log(JSON.stringify(message, undefined, 2));
    }
   */

  /** This request is sent by Nchan to the app server's ip address so we don't know which site
    * it concerns (because the normal functionality that looks at the hostname doesn't work,
    * since Nchan sends to the ip address, not the correct hostname).
    * However we get the site id (and user id) in the channel id url param.   ... But ...
    *
    * ... But, Nchan *does* apparently include headers and cookies from the original request.
    * So, below, we compare the 'siteId-userId' in the specified Nchan channel,
    * with the site id and user id in the host header & sessiond id hash.
    */
  def authorizeSubscriber(channelId: String) = GetAction { request =>
    SECURITY ; COULD // include a xsrf token? They're normally used for post requests only,
    // but perhaps it makes sense to prevent someone from tricking a browser to subscribe
    // to events? Not sure what harm that could do, but ... add xsrf token just in case?

    // If the user has logged in, we've verified the session cookie & user id therein already.
    // Only need to verify that it matches the user id specified in the channelId url param.
    // (nchan will subscribe the browser to all events the server sends to a channel
    // with id = 'siteId-userId')

    // The channel contains the site id, so we won't accidentally send messages to browser
    // at the wrong site. [7YGK082]
    var isTestSite: Boolean = false
    val (siteIdString, dashUserId) = {
      isTestSite = channelId.headOption.contains('-')
      val c = if (isTestSite) channelId.drop(1) else channelId
      c.span(_ != '-')
    }

    if (dashUserId.isEmpty)
      throwForbidden("EsE5GU0W2", s"Bad channel id: $channelId")

    var siteId = siteIdString.toIntOrThrow(
      "EdE2WDSX7", s"Bad channel site id, not an integer: $siteIdString")
    if (isTestSite) {
      siteId = -siteId
    }

    if (siteId != request.siteId)
      throwForbidden("EsE4FK20X", s"Bad site id: $siteId, should be: ${request.siteId}")

    val userIdString = dashUserId.drop(1)
    val userId = userIdString.toIntOption getOrElse throwForbidden(
      "EsE26GKW2", s"Bad user id in channel id: $channelId")

    // (This'd be suspect. Perhaps log something in some suspicious ip addresses log?)
    if (request.theUserId != userId)
      throwForbidden("EsE7UMJ2", s"Wrong user id, cookie: ${request.theUserId} != url: $userId")

    /*
    // For now, guests may not subscribe. Perhaps later somehow, or in some different ways.
    // Perhaps per topic channels? Instead of per user. For guests, only?
    val sessionCookieUserId = request.sidStatus.roleId getOrElse throwForbidden(
      "EsE5UJGKF2", "Not logged in as a site member")
      */

    SECURITY; COULD // secret-salt hash the 'siteId-userId' and include-append in the channel id,
    // to make it extra impossible to listen to someone else's 'siteId-userId' events. Not really
    // needed though, because site & user id already secure-salt hashed in the session id [4WKRQ1A]
    SECURITY; TESTS_MISSING // test that cannot specify the wrong host HTTP param or the wrong
    // 'siteId-userId' channel id, and in that way subscribe e.g. as someone else at the same site,
    // or someone with the same user id, at a different site.

    /*
    RACE // fairly harmless though. If the user updates the watchbar vi another browser tab right now.
    val watchbar: BareWatchbar = request.dao.getOrCreateWatchbar(request.theUser.id)
    globals.pubSub.userSubscribed(request.siteId, request.theUser, request.theBrowserIdData,
      watchbar.watchedPageIds)
     */
    Ok
  }


  def loadOnlineUsers(): Action[Unit] = GetActionRateLimited(RateLimits.ExpensiveGetRequest) {
        request =>
    val stuff = request.dao.loadUsersOnlineStuff()
    OkSafeJson(
      Json.obj(
        "numOnlineStrangers" -> stuff.numStrangers,
        "onlineUsers" -> stuff.usersJson))
  }


  private def lookupSiteId(host: String): SiteId = {
    COULD // use a cache. hostname --> site id won't change
    val siteId = globals.systemDao.lookupCanonicalHost(host) match {
      case Some(result) =>
        if (result.thisHost == result.canonicalHost)
          result.siteId
        else result.thisHost.role match {
          case Hostname.RoleDuplicate =>
            result.siteId
          case Hostname.RoleRedirect =>
            throwForbidden("EsE6U80K3", s"May not subscribe to a RoleRedirect host: $host")
          case Hostname.RoleLink =>
            die("EsE4GUK20", "Not implemented: <link rel='canonical'>")
          case _ =>
            die("EsE2WKF7")
        }
      case None =>
        throwNotFound("DwE2GKU80", s"Non-existing host: $host")
    }
    siteId
  }

}

