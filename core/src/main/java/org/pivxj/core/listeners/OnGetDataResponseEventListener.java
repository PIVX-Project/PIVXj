package org.pivxj.core.listeners;

import host.furszy.zerocoinj.protocol.PubcoinsMessage;

public interface OnGetDataResponseEventListener {

    void onResponseReceived(PubcoinsMessage pubcoinsMessage);

}

