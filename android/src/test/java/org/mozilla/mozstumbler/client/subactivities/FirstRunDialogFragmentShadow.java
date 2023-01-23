/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import static org.robolectric.Robolectric.directlyOn;


@Implements(Fragment.class)
public class FirstRunDialogFragmentShadow {

    @RealObject
    private DialogFragment realFragment;

    private boolean returnNullActivity;

    public void returnNullActivity(boolean v) {
        this.returnNullActivity = v;
    }

    @Implementation
    final public AppCompatActivity getActivity() {
        if (this.returnNullActivity) {
            return null;
        }
        return directlyOn(realFragment, Fragment.class).getActivity();
    }

}
