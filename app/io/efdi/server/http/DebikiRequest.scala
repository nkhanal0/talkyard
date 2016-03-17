/**
 * Copyright (c) 2012-2015 Kaj Magnus Lindberg
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

package io.efdi.server.http

import com.debiki.core._
import debiki.DebikiHttp._
import debiki._
import debiki.dao.SiteDao
import java.{util => ju}
import play.api.mvc
import play.api.mvc.{Action => _, _}


/**
 */
abstract class DebikiRequest[A] {

  def siteIdAndCanonicalHostname: SiteIdHostname
  def sid: SidStatus
  def xsrfToken: XsrfOk
  def browserId: BrowserId
  def user: Option[User]
  def dao: SiteDao
  def request: Request[A]

  def underlying = request

  require(siteIdAndCanonicalHostname.id == dao.siteId, "EsE76YW2")
  require(user.map(_.id) == sid.userId, "EsE7PUUY2")

  def tenantId = dao.siteId
  def siteId = dao.siteId
  def canonicalHostname = siteIdAndCanonicalHostname.hostname
  def domain = request.domain

  def siteSettings: EffectiveSettings = dao.loadWholeSiteSettings()

  def theBrowserIdData = BrowserIdData(ip = ip, idCookie = browserId.cookieValue,
    fingerprint = 0) // skip for now

  def browserIdIsNew = browserId.isNew

  def theUser = user_!
  def theUserId = theUser.id

  def user_! : User =
    user getOrElse throwForbidden("DwE86Wb7", "Not logged in")

  def anyRoleId = user.flatMap(_.anyRoleId)
  def theRoleId = anyRoleId getOrElse throwForbidden("DwE86Wb7", "Not authenticated")

  def isGuest = user.exists(_.isGuest)
  def isAuthenticated = user.exists(_.isAuthenticated)
  def isApprovedOrStaff = user.exists(_.isApprovedOrStaff)
  def isStaff = user.exists(_.isStaff)

  /**
   * The display name of the user making the request. Throws 403 Forbidden
   * if not available, i.e. if not logged in (shouldn't happen normally).
   */
  def displayName_! : String =
    sid.displayName getOrElse throwForbidden("DwE97Ik3", "Not logged in")

  def session: mvc.Session = request.session

  def ip = realOrFakeIpOf(request)

  /**
   * Approximately when the server started serving this request.
   */
  lazy val ctime: ju.Date = new ju.Date

  /** The scheme, host and port specified in the request. */
  def origin: String = s"$scheme://$host"

  def scheme = if (request.secure) "https" else "http"

  def host = request.host
  def hostname = request.host.span(_ != ':')._1

  def colonPort = request.host.dropWhile(_ != ':')

  def uri = request.uri

  def queryString = request.queryString

  def rawQueryString = request.rawQueryString

  def body = request.body

  def headers = request.headers

  def cookies = request.cookies

  def isAjax = DebikiHttp.isAjax(request)

  def isHttpPostRequest = request.method == "POST"

  def httpVersion = request.version

}
