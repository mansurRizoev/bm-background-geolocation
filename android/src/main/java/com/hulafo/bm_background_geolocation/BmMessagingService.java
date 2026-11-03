package com.hulafo.bm_background_geolocation;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Default FCM receiver of the plugin (registered with a low priority, so a service of the host app wins).
 * If the host app has its own FirebaseMessagingService, call {@link BmFcm#handleMessage} from it instead.
 */
public class BmMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        BmFcm.handleMessage(this, message.getData());
    }
}
