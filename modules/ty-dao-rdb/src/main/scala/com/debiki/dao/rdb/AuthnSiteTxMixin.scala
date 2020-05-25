/**
 * Copyright (C) 2011-2015, 2020 Kaj Magnus Lindberg
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

package com.debiki.dao.rdb

import com.debiki.core._
import com.debiki.core.Prelude._
import com.debiki.core.Participant.{LowestNonGuestId, LowestAuthenticatedUserId}
import _root_.java.{util => ju, io => jio}
import java.{sql => js}
import scala.collection.immutable
import scala.collection.mutable.ArrayBuffer
import Rdb._
import RdbUtil._


/** Creates and updates users and identities.  Docs [8KFUT20].
  */
trait AuthnSiteTxMixin extends SiteTransaction {
  self: RdbSiteTransaction =>

  /* ...
  def nextMemberId: UserId = {
    val query = s"""
      select max(user_id) max_id from users3
      where site_id = ? and user_id >= $LowestAuthenticatedUserId
      """
    runQueryFindExactlyOne(query, List(siteId.asAnyRef), rs => {
      val maxId = rs.getInt("max_id")
      math.max(LowestAuthenticatedUserId, maxId + 1)
    })
  } */


  def nextIdentityId: IdentityId = {
    val query = s"""
      select max(id) max_id from identities3
      where site_id = ?
      """
    runQueryFindExactlyOne(query, List(siteId.asAnyRef), rs => {
      val maxId = rs.getInt("max_id")
      (maxId + 1).toString
    })
  }

  /*
  def insertGuest(guest: Guest) {
        throw ex
    }
  def insertMember(user: UserInclDetails) {
  }  */


  def deleteAllUsersIdentities(userId: UserId) {
    TESTS_MISSING
    val statement =
      "delete from identities3 where user_id = ? and site_id = ?"
    val values = List[AnyRef](userId.asAnyRef, siteId.asAnyRef)
    runUpdate(statement, values)
  }


  def insertIdentity(identity: Identity) {
    identity match {
      case x: IdentityOpenId => // ???
        // insertOpenIdIdentity(siteId, x)   ! remove  siteId  below
      case x: OpenAuthIdentity =>
        insertOpenAuthIdentity(siteId, x)
      case x =>
        die("DwE8UYM0", s"Unknown identity type: ${classNameOf(x)}")
    }
  }

  /*
  private[rdb] def insertOpenIdIdentity(otherSiteId: SiteId, identity: IdentityOpenId) {
    val details = identity.openIdDetails
    runUpdate("""
            insert into identities3(
                ID, SITE_ID, USER_ID, USER_ID_ORIG, OID_CLAIMED_ID, OID_OP_LOCAL_ID,
                OID_REALM, OID_ENDPOINT, OID_VERSION,
                FIRST_NAME, EMAIL, COUNTRY)
            values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            List[AnyRef](identity.id.toInt.asAnyRef, otherSiteId.asAnyRef, identity.userId.asAnyRef,
              identity.userId.asAnyRef,
              details.oidClaimedId, e2d(details.oidOpLocalId), e2d(details.oidRealm),
              e2d(details.oidEndpoint), e2d(details.oidVersion),
              e2d(details.firstName), details.email.orNullVarchar, e2d(details.country.trim)))
  }


  private[rdb] def _updateIdentity(identity: IdentityOpenId)
        (implicit connection: js.Connection) {
    val details = identity.openIdDetails
    db.update("""
      update identities3 set
          USER_ID = ?, OID_CLAIMED_ID = ?,
          OID_OP_LOCAL_ID = ?, OID_REALM = ?,
          OID_ENDPOINT = ?, OID_VERSION = ?,
          FIRST_NAME = ?, EMAIL = ?, COUNTRY = ?
      where ID = ? and SITE_ID = ?
      """,
      List[AnyRef](identity.userId.asAnyRef, details.oidClaimedId,
        e2d(details.oidOpLocalId), e2d(details.oidRealm),
        e2d(details.oidEndpoint), e2d(details.oidVersion),
        e2d(details.firstName), details.email.orNullVarchar, e2d(details.country.trim),
        identity.id.toInt.asAnyRef, siteId.asAnyRef))
  }  */


  private def insertOpenAuthIdentity(
        otherSiteId: SiteId, identity: OpenAuthIdentity) {
    val sql = """
        insert into identities3(
            ID, SITE_ID, USER_ID, USER_ID_ORIG,
            FIRST_NAME, LAST_NAME, FULL_NAME, EMAIL, AVATAR_URL,
            AUTH_METHOD, SECURESOCIAL_PROVIDER_ID, SECURESOCIAL_USER_ID)
        values (
            ?, ?, ?, ?,
            ?, ?, ?, ?, ?,
            ?, ?, ?)"""
    val ds = identity.openAuthDetails
    val method = "OAuth" // should probably remove this column
    val values = List[AnyRef](
      identity.id.toInt.asAnyRef, otherSiteId.asAnyRef, identity.userId.asAnyRef,
      identity.userId.asAnyRef,
      ds.firstName.orNullVarchar, ds.lastName.orNullVarchar,
      ds.fullName.orNullVarchar, ds.email.orNullVarchar, ds.avatarUrl.orNullVarchar,
      method, ds.providerId, ds.providerKey)
    runUpdate(sql, values)
  }


  private[rdb] def updateOpenAuthIdentity(identity: OpenAuthIdentity)
        (implicit connection: js.Connection) {
    val sql = """
      update identities3 set
        USER_ID = ?, AUTH_METHOD = ?,
        FIRST_NAME = ?, LAST_NAME = ?, FULL_NAME = ?, EMAIL = ?, AVATAR_URL = ?
      where ID = ? and SITE_ID = ?"""
    val ds = identity.openAuthDetails
    val method = "OAuth" // should probably remove this column
    val values = List[AnyRef](
      identity.userId.asAnyRef, method, ds.firstName.orNullVarchar, ds.lastName.orNullVarchar,
      ds.fullName.orNullVarchar, ds.email.orNullVarchar, ds.avatarUrl.orNullVarchar,
      identity.id.toInt.asAnyRef, siteId.asAnyRef)
    db.update(sql, values)
  }


  def loadAllIdentities(): immutable.Seq[Identity] = {
    loadIdentitiesImpl(userId = None)
  }


  def loadIdentities(userId: UserId): immutable.Seq[Identity] = {
    loadIdentitiesImpl(Some(userId))
  }


  private def loadIdentitiesImpl(userId: Option[UserId]): immutable.Seq[Identity] = {
    val values = ArrayBuffer(siteId.asAnyRef)
    val andIdEq = userId.map(id => {
      values.append(id.asAnyRef)
      "and i.user_id = ?"
    }) getOrElse ""
    val query = s"""
        select $IdentitySelectListItems
        from identities3 i
        where i.site_id = ? $andIdEq"""
    runQueryFindMany(query, values.toList, rs => {
      val identity = readIdentity(rs)
      dieIf(userId.exists(_ != identity.userId), "TyE2WKBGE5")
      identity
    })
  }


  def loadOpenAuthIdentity(openAuthKey: OpenAuthProviderIdKey): Option[OpenAuthIdentity] = {
    val query = s"""
        select $IdentitySelectListItems
        from identities3 i
        where i.site_id = ?
          and i.securesocial_provider_id = ?
          and i.securesocial_user_id = ?"""
    val values = List(siteId.asAnyRef, openAuthKey.providerId, openAuthKey.providerKey)
    // There's a unique key.
    runQueryFindOneOrNone(query, values, rs => {
      val identity = readIdentity(rs)
      dieIf(!identity.isInstanceOf[OpenAuthIdentity], "TyE5WKB2A1", "Bad class: " + classNameOf(identity))
      val openAuthIdentity = identity.asInstanceOf[OpenAuthIdentity]
      dieIf(openAuthIdentity.openAuthDetails.providerId != openAuthKey.providerId, "TyE2KWB01")
      dieIf(openAuthIdentity.openAuthDetails.providerKey != openAuthKey.providerKey, "TyE2KWB02")
      openAuthIdentity
    })
  }

  /*
  def loadOpenIdIdentity(openIdDetails: OpenIdDetails): Option[IdentityOpenId] = {
    unimplemented("loadOpenIdIdentity", "TyE2WKBP40") /*
    Maybe reuse later on, if loading OpenId identities:
    val query = s"""
        select $IdentitySelectListItems
        from identities3 i
        where i.site_id = ?
          and ???
        """
    val values = ??? List(siteId.asAnyRef, ... )
      case (None, Some(openIdDetails: OpenIdDetails), None) =>
        // With Google OpenID, the identifier varies by realm. So use email
        // address instead. (With Google OpenID, the email address can be
        // trusted — this is not the case, however, in general.
        // See: http://blog.stackoverflow.com/2010/04/openid-one-year-later/
        // Quote:
        //  "If we have an email address from a verified OpenID email
        //   provider (that is, an OpenID from a large email service we trust,
        //   like Google or Yahoo), then it’s guaranteed to be a globally
        //   unique string.")
        val (claimedIdOrEmailCheck, idOrEmail) = {
          // SECURITY why can I trust the OpenID provider to specify
          // the correct endpoint? What if Mallory's provider replies
          // with Googles endpoint? I guess the Relying Party impl doesn't
          // allow this but anyway, I'd like to know for sure.
          ("i.OID_CLAIMED_ID = ?", openIdDetails.oidClaimedId)
        }
        ("""where i.SITE_ID = ?
            and """+ claimedIdOrEmailCheck +"""
          """, List(siteId.asAnyRef, idOrEmail))
    runQueryFindMany(query, values, rs => {
      val identity = readIdentity(rs)
    }) */
  } */


  private val IdentitySelectListItems = i"""
     |id identity_id,
     |user_id,
     |oid_claimed_id,
     |oid_op_local_id,
     |oid_realm,
     |oid_endpoint,
     |oid_version,
     |securesocial_user_id,
     |securesocial_provider_id,
     |auth_method,
     |first_name i_first_name,
     |last_name i_last_name,
     |full_name i_full_name,
     |email i_email,
     |country i_country,
     |avatar_url i_avatar_url
   """


  def readIdentity(rs: js.ResultSet): Identity = {
    val identityId = rs.getInt("identity_id").toString
    val userId = rs.getInt("user_id")

    val email = Option(rs.getString("i_email"))
    val anyClaimedOpenId = Option(rs.getString("OID_CLAIMED_ID"))
    val anyOpenAuthProviderId = Option(rs.getString("SECURESOCIAL_PROVIDER_ID"))

    val identityInDb = {
      if (anyClaimedOpenId.nonEmpty) {
        IdentityOpenId(
          id = identityId,
          userId = userId,
          // COULD use d2e here, or n2e if I store Null instead of '-'.
          OpenIdDetails(
            oidEndpoint = rs.getString("OID_ENDPOINT"),
            oidVersion = rs.getString("OID_VERSION"),
            oidRealm = rs.getString("OID_REALM"),
            oidClaimedId = anyClaimedOpenId.get,
            oidOpLocalId = rs.getString("OID_OP_LOCAL_ID"),
            firstName = rs.getString("i_first_name"),
            email = email,
            country = rs.getString("i_country")))
      }
      else if (anyOpenAuthProviderId.nonEmpty) {
        OpenAuthIdentity(
          id = identityId,
          userId = userId,
          openAuthDetails = OpenAuthDetails(
            providerId = anyOpenAuthProviderId.get,
            providerKey = rs.getString("SECURESOCIAL_USER_ID"),
            firstName = Option(rs.getString("i_first_name")),
            lastName = Option(rs.getString("i_last_name")),
            fullName = Option(rs.getString("i_full_name")),
            email = email,
            avatarUrl = Option(rs.getString("i_avatar_url"))))
      }
      else {
        die("TyE77GJ2", s"s$siteId: Unknown identity type, id: $identityId, user: $userId")
      }
    }
    identityInDb
  }

}



