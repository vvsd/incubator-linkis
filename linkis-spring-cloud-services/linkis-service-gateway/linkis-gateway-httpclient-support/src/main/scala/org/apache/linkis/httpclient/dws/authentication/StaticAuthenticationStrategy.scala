/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.linkis.httpclient.dws.authentication

import org.apache.linkis.common.utils.ByteTimeUtils
import org.apache.linkis.httpclient.authentication.{
  AbstractAuthenticationStrategy,
  Authentication,
  AuthenticationAction,
  AuthenticationResult
}
import org.apache.linkis.httpclient.dws.exception.AuthenticationFailedException
import org.apache.linkis.httpclient.dws.request.DWSAuthenticationAction
import org.apache.linkis.httpclient.dws.response.DWSAuthenticationResult
import org.apache.linkis.httpclient.request.{Action, UserAction, UserPwdAction}

import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpResponse

class StaticAuthenticationStrategy(override protected val sessionMaxAliveTime: Long)
    extends AbstractAuthenticationStrategy {
  def this() = this(ByteTimeUtils.timeStringAsMs("1h"))

  private var serverSessionTimeout: Long = sessionMaxAliveTime

  def setServerSessionTimeout(serverSessionTimeout: Long): Unit = {
    this.serverSessionTimeout = serverSessionTimeout
  }

  def getServerSessionTimeOut: Long = this.serverSessionTimeout

  override protected def getUser(requestAction: Action): String = requestAction match {
    case _: AuthenticationAction => null
    case authAction: UserAction =>
      // If UserAction not contains user, then use the authTokenKey by default.
      if (StringUtils.isBlank(authAction.getUser)) getClientConfig.getAuthTokenKey
      else authAction.getUser
    case _ if StringUtils.isNotBlank(getClientConfig.getAuthTokenKey) =>
      getClientConfig.getAuthTokenKey
    case _ => null
  }

  override protected def getAuthenticationAction(
      requestAction: Action,
      serverUrl: String
  ): AuthenticationAction = {
    val action = new DWSAuthenticationAction(serverUrl)

    def pwd: String = if (StringUtils.isNotBlank(getClientConfig.getAuthTokenValue)) {
      getClientConfig.getAuthTokenValue
    } else {
      throw new AuthenticationFailedException(
        "the value of authTokenValue in ClientConfig must be exists, since no password is found to login."
      )
    }

    requestAction match {
      case userPwd: UserPwdAction =>
        action.addRequestPayload("userName", userPwd.getUser)
        action.addRequestPayload("password", userPwd.getPassword.getOrElse(pwd))
      case userAction: UserAction =>
        action.addRequestPayload("userName", userAction.getUser)
        action.addRequestPayload("password", pwd)
      case _ =>
        if (StringUtils.isBlank(getClientConfig.getAuthTokenKey)) {
          throw new AuthenticationFailedException(
            "the value of authTokenKey in ClientConfig must be exists, since no user is found to login."
          )
        }
        action.addRequestPayload("userName", getClientConfig.getAuthTokenKey)
        action.addRequestPayload("password", pwd)
    }
    action
  }

  override def getAuthenticationResult(
      response: HttpResponse,
      requestAction: AuthenticationAction
  ): AuthenticationResult = {
    val result = new DWSAuthenticationResult(response, requestAction.serverUrl)
    val timeout = result.getData.get("sessionTimeOut")
    // update session timeout
    if (null != timeout) {
      setServerSessionTimeout(timeout.toString.toLong)
    }
    result
  }

  override def isTimeout(authentication: Authentication): Boolean =
    System.currentTimeMillis() - authentication.getLastAccessTime >= serverSessionTimeout

  override def enforceLogin(requestAction: Action, serverUrl: String): Authentication = {
    val key = getKey(requestAction, serverUrl)
    if (key == null) return null
    key.intern() synchronized {
      val authentication = tryLogin(requestAction, serverUrl)
      putSession(key, authentication)
      authentication
    }
  }

}
