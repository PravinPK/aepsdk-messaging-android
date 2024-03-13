/*
 Copyright 2021 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.messagingsample

import android.app.Application
import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Messaging
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.edge.identity.Identity
import com.google.firebase.messaging.FirebaseMessaging

class MessagingApplication : Application() {
    private val ENVIRONMENT_FILE_ID = "bf7248f92b53/779ef92f7d96/launch-fa59caaf08bb-development"
    private val ASSURANCE_SESSION_ID = ""

    override fun onCreate() {
        super.onCreate()

        MobileCore.setApplication(this)
        MobileCore.setLogLevel(LoggingMode.VERBOSE)
        // Assurance.startSession(ASSURANCE_SESSION_ID)
        val extensions = listOf(Messaging.EXTENSION, Identity.EXTENSION, Lifecycle.EXTENSION, Edge.EXTENSION, Assurance.EXTENSION)
        MobileCore.registerExtensions(extensions) {
            // Necessary property id which has the edge configuration id needed by aep sdk
            MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID)
            MobileCore.lifecycleStart(null)
            Assurance.startSession("lab://?adb_validation_sessionid=6679e768-87d6-4be1-a8ce-539c22e39445")
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            // Log and toast
            if (task.isSuccessful) {
                // Get new FCM registration token
                val token = task.result
                print("MessagingApplication Firebase token :: $token")
                // Syncing the push token with experience platform
                MobileCore.setPushIdentifier(token)
            }
        }
    }
}