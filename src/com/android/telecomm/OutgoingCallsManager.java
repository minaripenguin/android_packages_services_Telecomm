/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.telecomm;

import android.telecomm.CallServiceDescriptor;

import com.android.internal.telecomm.ICallServiceSelector;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Responsible for placing all outgoing calls. For each outgoing call, this class creates an
 * instance of {@link OutgoingCallProcessor} which handles the details of connecting to the
 * appropriate call service and placing the call. This class maintains a mapping from call
 * to {@link OutgoingCallProcessor} so that other classes (CallServiceAdapter, etc),
 * can simply call into this class instead of individual OutgoingCallProcessors.
 */
final class OutgoingCallsManager {

    /**
     * Maps call to {@link OutgoingCallProcessor}s.
     */
    private Map<Call, OutgoingCallProcessor> mOutgoingCallProcessors = Maps.newHashMap();

    /**
     * Starts the process of placing a call by constructing an outgoing call processor and asking
     * it to place the call. Upon success, execution will continue (via {@link CallServiceAdapter})
     * to {@link #handleSuccessfulCallAttempt}. Upon failure, execution will return to
     * {@link #handleFailedCallAttempt}.
     *
     * @param call The call to place.
     * @param callServices The collection of call services which can potentially place the call.
     * @param selectors The ordered list of selectors used in placing the call.
     */
    void placeCall(
            Call call,
            Collection<CallServiceWrapper> callServices,
            Collection<CallServiceSelectorWrapper> selectors) {

        Log.i(this, "Placing an outgoing call: %s", call);

        // Create the processor for this (outgoing) call and store it in a map such that call
        // attempts can be aborted etc.
        // TODO(gilad): Consider passing mSelector as an immutable set.
        OutgoingCallProcessor processor =
                new OutgoingCallProcessor(call, callServices, selectors, this);

        mOutgoingCallProcessors.put(call, processor);
        processor.process();
    }

    /**
     * Forwards the compatibility status from the call-service to the corresponding outgoing-call
     * processor.
     *
     * @param isCompatible True if the call-service is compatible with the call.
     */
    void setIsCompatibleWith(Call call, boolean isCompatible) {
        Log.v(this, "setIsCompatibleWith, call %s, isCompatible: %b", call, isCompatible);
        OutgoingCallProcessor processor = mOutgoingCallProcessors.get(call);
        if (processor == null) {
            // Shouldn't happen, so log a wtf if it does.
            Log.wtf(this, "Received unexpected setCompatibleWith notification.");
        } else {
            processor.setIsCompatibleWith(call, isCompatible);
        }
    }

    /**
     * Removes the outgoing call processor mapping for the successful call and returns execution to
     * the call. This method is invoked from {@link CallServiceAdapter} after a call service
     * has notified Telecomm that it successfully placed the call.
     */
    void handleSuccessfulCallAttempt(Call call) {
        Log.v(this, "handleSuccessfulCallAttempt, call: %s", call);
        OutgoingCallProcessor processor = mOutgoingCallProcessors.remove(call);

        if (processor == null) {
            // Shouldn't happen, so log a wtf if it does.
            Log.wtf(this, "Received an unexpected placed-call notification.");
        } else {
            processor.handleSuccessfulCallAttempt();
        }
    }

    /**
     * Notifies the appropriate outgoing call processor that a call attempt to place the call has
     * failed and the processor should continue attempting to place the call with the next call
     * service. This method is called from {@link CallServiceAdapter} after a call service has
     * notified Telecomm that it could not place the call.
     *
     * @param reason The call-service supplied reason for the failed call attempt.
     */
    void handleFailedCallAttempt(Call call, String reason) {
        Log.v(this, "handleFailedCallAttempt, call: %s, reason: %s", call, reason);
        OutgoingCallProcessor processor = mOutgoingCallProcessors.get(call);

        if (processor == null) {
            // Shouldn't happen, so log a wtf if it does.
            Log.wtf(this, "Received an unexpected failed-call notification.");
        } else {
            processor.handleFailedCallAttempt(reason);
        }
    }

    /**
     * Removes the outgoing call processor mapping for the failed call and returns execution to the
     * call. In contrast to handleFailedCallAttempt which comes from the call-service and
     * goes to the outgoing-call processor indicating a single failed call attempt, this method is
     * invoked by the outgoing-call processor to indicate that the entire process has failed and we
     * should cleanup and notify the call.
     *
     * @param call The failed outgoing call.
     * @param isAborted True if the call timedout and is aborted.
     */
    void handleFailedOutgoingCall(Call call, boolean isAborted) {
        Log.v(this, "handleFailedOutgoingCall, call: %s", call);
        mOutgoingCallProcessors.remove(call);
        call.handleFailedOutgoing(isAborted);
    }

    /**
     * Forwards the selected call service from the selector to the corresponding outgoing-call
     * processor.
     */
    void processSelectedCallServices(Call call, List<CallServiceDescriptor> descriptors) {
        Log.v(this, "processSelectedCallServices, call %s,  descriptors: %s", call, descriptors);
        OutgoingCallProcessor processor = mOutgoingCallProcessors.get(call);
        if (processor == null) {
            // Shouldn't happen, so log a wtf if it does.
            Log.wtf(this, "Received unexpected setSelectedCallServices notification.");
        } else {
            processor.processSelectedCallServices(descriptors);
        }
    }

    /**
     * Aborts any ongoing attempts to connect the specified (outgoing) call.
     *
     * @param call The call to be aborted.
     * @return False if the call was not found; True otherwise, indicating that the abort was
     *         successful.
     */
    boolean abort(Call call) {
        OutgoingCallProcessor processor = mOutgoingCallProcessors.remove(call);
        if (processor != null) {
            Log.v(this, "abort, call: %s", call);
            processor.abort();
            return true;
        }

        return false;
    }
}
