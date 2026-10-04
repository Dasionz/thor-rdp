package com.iiordanov.bVNC;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.test.InstrumentationTestCase;

import net.sqlcipher.database.SQLiteDatabase;

import java.io.File;

/** Exercises the real SQLCipher helper on isolated databases, never user profiles. */
public class RdpAudioMigrationTest extends InstrumentationTestCase {
    private File databaseFile;

    private Context isolatedContext(String packageName) throws Exception {
        Context target = getInstrumentation().getTargetContext();
        databaseFile = File.createTempFile("rdp-audio-migration-", ".db", target.getCacheDir());
        assertTrue(databaseFile.delete());
        return new ContextWrapper(target) {
            @Override public String getPackageName() { return packageName; }
            @Override public File getDatabasePath(String name) { return databaseFile; }
        };
    }

    private void createLegacyDatabase(Context context) {
        SQLiteDatabase.loadLibs(context);
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(databaseFile, "", null)) {
            db.execSQL(AbstractConnectionBean.GEN_CREATE);
            insertSound(db, 1, Constants.REMOTE_SOUND_DISABLED);
            insertSound(db, 2, Constants.REMOTE_SOUND_ON_SERVER);
            insertSound(db, 3, Constants.REMOTE_SOUND_ON_DEVICE);
            db.setVersion(641); // Exact database version shipped in Thor 0.1.0.
        }
    }

    private void insertSound(SQLiteDatabase db, long id, int soundType) {
        ContentValues values = new ContentValues();
        values.put(AbstractConnectionBean.GEN_FIELD__ID, id);
        values.put(AbstractConnectionBean.GEN_FIELD_REMOTESOUNDTYPE, soundType);
        assertTrue(db.insertOrThrow(AbstractConnectionBean.GEN_TABLE_NAME, null, values) >= 0);
    }

    private int sound(SQLiteDatabase db, long id) {
        try (Cursor cursor = db.query(AbstractConnectionBean.GEN_TABLE_NAME,
                new String[]{AbstractConnectionBean.GEN_FIELD_REMOTESOUNDTYPE},
                AbstractConnectionBean.GEN_FIELD__ID + " = ?", new String[]{Long.toString(id)},
                null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    public void testUpgradeRepairsForcedDisabledOnlyOnce() throws Exception {
        Context context = isolatedContext("dev.thor.freerdp");
        createLegacyDatabase(context);
        Database helper = new Database(context);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(642, db.getVersion());
            assertEquals(Constants.REMOTE_SOUND_ON_DEVICE, sound(db, 1));
            assertEquals(Constants.REMOTE_SOUND_ON_SERVER, sound(db, 2));
            assertEquals(Constants.REMOTE_SOUND_ON_DEVICE, sound(db, 3));
            ContentValues disabled = new ContentValues();
            disabled.put(AbstractConnectionBean.GEN_FIELD_REMOTESOUNDTYPE, Constants.REMOTE_SOUND_DISABLED);
            db.update(AbstractConnectionBean.GEN_TABLE_NAME, disabled,
                    AbstractConnectionBean.GEN_FIELD__ID + " = ?", new String[]{"1"});
        } finally {
            helper.close();
        }
        helper = new Database(context);
        try {
            assertEquals(Constants.REMOTE_SOUND_DISABLED, sound(helper.getReadableDatabase(), 1));
        } finally {
            helper.close();
        }
    }

    public void testOtherFlavorsKeepExistingAudioAndDatabaseVersion() throws Exception {
        Context context = isolatedContext("com.iiordanov.freeaRDP");
        createLegacyDatabase(context);
        Database helper = new Database(context);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            assertEquals(641, db.getVersion());
            assertEquals(Constants.REMOTE_SOUND_DISABLED, sound(db, 1));
            assertEquals(Constants.REMOTE_SOUND_ON_SERVER, sound(db, 2));
            assertEquals(Constants.REMOTE_SOUND_ON_DEVICE, sound(db, 3));
        } finally {
            helper.close();
        }
    }

    public void testNewThorConnectionsDefaultToDevicePlayback() throws Exception {
        Context context = isolatedContext("dev.thor.freerdp");
        assertEquals(Constants.REMOTE_SOUND_ON_DEVICE, new ConnectionBean(context).getRemoteSoundType());
        Database helper = new Database(context);
        try {
            assertEquals(642, helper.getWritableDatabase().getVersion());
        } finally {
            helper.close();
        }
    }

    @Override protected void tearDown() throws Exception {
        if (databaseFile != null) android.database.sqlite.SQLiteDatabase.deleteDatabase(databaseFile);
        super.tearDown();
    }
}
