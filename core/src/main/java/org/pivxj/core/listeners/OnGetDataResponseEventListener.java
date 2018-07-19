package org.pivxj.core.listeners;

import org.pivxj.zerocoin.PubcoinsMessage;

public interface OnGetDataResponseEventListener {

    void onResponseReceived(PubcoinsMessage pubcoinsMessage);

}

