/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.helper.ExportHelper;

public class KeyListActivity extends DrawerActivity {

    ExportHelper mExportHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportHelper = new ExportHelper(this);

        setContentView(R.layout.key_list_activity);

        // now setup navigation drawer in DrawerActivity...
        setupDrawerNavigation(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.key_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_key_list_import:
                importKeys();
                return true;

            case R.id.menu_key_list_create:
                createKey();
                return true;

            case R.id.menu_key_list_create_expert:
                createKeyExpert();
                return true;

            case R.id.menu_key_list_export:
                mExportHelper.showExportKeysDialog(null, Constants.Path.APP_DIR_FILE, true);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void importKeys() {
        Intent intent = new Intent(this, ImportKeysActivity.class);
        startActivityForResult(intent, 0);
    }

    private void createKey() {
        Intent intent = new Intent(this, EditKeyActivity.class);
        intent.setAction(EditKeyActivity.ACTION_CREATE_KEY);
        intent.putExtra(EditKeyActivity.EXTRA_GENERATE_DEFAULT_KEYS, true);
        intent.putExtra(EditKeyActivity.EXTRA_USER_IDS, ""); // show user id view
        startActivityForResult(intent, 0);
    }

    private void createKeyExpert() {
        Intent intent = new Intent(this, EditKeyActivity.class);
        intent.setAction(EditKeyActivity.ACTION_CREATE_KEY);
        startActivityForResult(intent, 0);
    }

}
