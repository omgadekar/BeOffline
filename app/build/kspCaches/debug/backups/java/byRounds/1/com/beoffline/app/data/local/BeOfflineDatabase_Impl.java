package com.beoffline.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BeOfflineDatabase_Impl extends BeOfflineDatabase {
  private volatile BlockRuleDao _blockRuleDao;

  private volatile OpenBlockRuleDao _openBlockRuleDao;

  private volatile AllowanceDao _allowanceDao;

  private volatile SoloTeaserStateDao _soloTeaserStateDao;

  private volatile PartnerDao _partnerDao;

  private volatile UnlockRequestCacheDao _unlockRequestCacheDao;

  private volatile OutboxDao _outboxDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(4) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `block_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `blockedPackages` TEXT NOT NULL, `ruleType` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `startHour` INTEGER, `startMinute` INTEGER, `endHour` INTEGER, `endMinute` INTEGER, `activeDays` TEXT, `timerDurationMinutes` INTEGER, `timerStartedAt` INTEGER, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `open_block_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `blockedPackages` TEXT NOT NULL, `ruleType` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `startHour` INTEGER, `startMinute` INTEGER, `endHour` INTEGER, `endMinute` INTEGER, `activeDays` TEXT, `timerDurationMinutes` INTEGER, `timerStartedAt` INTEGER, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `allowances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `ruleId` INTEGER NOT NULL, `grantedUntil` INTEGER NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `solo_teaser_state` (`ruleId` INTEGER NOT NULL, `sessionKey` TEXT NOT NULL, `unlockCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`ruleId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `partners` (`pairingId` TEXT NOT NULL, `partnerUid` TEXT NOT NULL, `partnerName` TEXT, `status` TEXT NOT NULL, `canApproveAfterUtc` INTEGER NOT NULL, `removalPending` INTEGER NOT NULL, `removalEffectiveAtUtc` INTEGER, `syncedAt` INTEGER NOT NULL, PRIMARY KEY(`pairingId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `unlock_request_cache` (`id` TEXT NOT NULL, `direction` TEXT NOT NULL, `packageName` TEXT NOT NULL, `appLabel` TEXT NOT NULL, `status` TEXT NOT NULL, `requesterName` TEXT, `requestedAtUtc` INTEGER NOT NULL, `expiresAtUtc` INTEGER, `grantedUntilUtc` INTEGER, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `outbox_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `clientKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6845c011f682bfcddcbd41ec92a10ac5')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `block_rules`");
        db.execSQL("DROP TABLE IF EXISTS `open_block_rules`");
        db.execSQL("DROP TABLE IF EXISTS `allowances`");
        db.execSQL("DROP TABLE IF EXISTS `solo_teaser_state`");
        db.execSQL("DROP TABLE IF EXISTS `partners`");
        db.execSQL("DROP TABLE IF EXISTS `unlock_request_cache`");
        db.execSQL("DROP TABLE IF EXISTS `outbox_items`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsBlockRules = new HashMap<String, TableInfo.Column>(13);
        _columnsBlockRules.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("blockedPackages", new TableInfo.Column("blockedPackages", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("ruleType", new TableInfo.Column("ruleType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("startHour", new TableInfo.Column("startHour", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("startMinute", new TableInfo.Column("startMinute", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("endHour", new TableInfo.Column("endHour", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("endMinute", new TableInfo.Column("endMinute", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("activeDays", new TableInfo.Column("activeDays", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("timerDurationMinutes", new TableInfo.Column("timerDurationMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("timerStartedAt", new TableInfo.Column("timerStartedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBlockRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBlockRules = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBlockRules = new TableInfo("block_rules", _columnsBlockRules, _foreignKeysBlockRules, _indicesBlockRules);
        final TableInfo _existingBlockRules = TableInfo.read(db, "block_rules");
        if (!_infoBlockRules.equals(_existingBlockRules)) {
          return new RoomOpenHelper.ValidationResult(false, "block_rules(com.beoffline.app.data.model.BlockRule).\n"
                  + " Expected:\n" + _infoBlockRules + "\n"
                  + " Found:\n" + _existingBlockRules);
        }
        final HashMap<String, TableInfo.Column> _columnsOpenBlockRules = new HashMap<String, TableInfo.Column>(13);
        _columnsOpenBlockRules.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("blockedPackages", new TableInfo.Column("blockedPackages", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("ruleType", new TableInfo.Column("ruleType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("startHour", new TableInfo.Column("startHour", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("startMinute", new TableInfo.Column("startMinute", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("endHour", new TableInfo.Column("endHour", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("endMinute", new TableInfo.Column("endMinute", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("activeDays", new TableInfo.Column("activeDays", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("timerDurationMinutes", new TableInfo.Column("timerDurationMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("timerStartedAt", new TableInfo.Column("timerStartedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOpenBlockRules.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOpenBlockRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOpenBlockRules = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoOpenBlockRules = new TableInfo("open_block_rules", _columnsOpenBlockRules, _foreignKeysOpenBlockRules, _indicesOpenBlockRules);
        final TableInfo _existingOpenBlockRules = TableInfo.read(db, "open_block_rules");
        if (!_infoOpenBlockRules.equals(_existingOpenBlockRules)) {
          return new RoomOpenHelper.ValidationResult(false, "open_block_rules(com.beoffline.app.data.model.OpenBlockRule).\n"
                  + " Expected:\n" + _infoOpenBlockRules + "\n"
                  + " Found:\n" + _existingOpenBlockRules);
        }
        final HashMap<String, TableInfo.Column> _columnsAllowances = new HashMap<String, TableInfo.Column>(6);
        _columnsAllowances.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAllowances.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAllowances.put("ruleId", new TableInfo.Column("ruleId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAllowances.put("grantedUntil", new TableInfo.Column("grantedUntil", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAllowances.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAllowances.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAllowances = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAllowances = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAllowances = new TableInfo("allowances", _columnsAllowances, _foreignKeysAllowances, _indicesAllowances);
        final TableInfo _existingAllowances = TableInfo.read(db, "allowances");
        if (!_infoAllowances.equals(_existingAllowances)) {
          return new RoomOpenHelper.ValidationResult(false, "allowances(com.beoffline.app.data.model.Allowance).\n"
                  + " Expected:\n" + _infoAllowances + "\n"
                  + " Found:\n" + _existingAllowances);
        }
        final HashMap<String, TableInfo.Column> _columnsSoloTeaserState = new HashMap<String, TableInfo.Column>(4);
        _columnsSoloTeaserState.put("ruleId", new TableInfo.Column("ruleId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSoloTeaserState.put("sessionKey", new TableInfo.Column("sessionKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSoloTeaserState.put("unlockCount", new TableInfo.Column("unlockCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSoloTeaserState.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSoloTeaserState = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSoloTeaserState = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSoloTeaserState = new TableInfo("solo_teaser_state", _columnsSoloTeaserState, _foreignKeysSoloTeaserState, _indicesSoloTeaserState);
        final TableInfo _existingSoloTeaserState = TableInfo.read(db, "solo_teaser_state");
        if (!_infoSoloTeaserState.equals(_existingSoloTeaserState)) {
          return new RoomOpenHelper.ValidationResult(false, "solo_teaser_state(com.beoffline.app.data.model.SoloTeaserState).\n"
                  + " Expected:\n" + _infoSoloTeaserState + "\n"
                  + " Found:\n" + _existingSoloTeaserState);
        }
        final HashMap<String, TableInfo.Column> _columnsPartners = new HashMap<String, TableInfo.Column>(8);
        _columnsPartners.put("pairingId", new TableInfo.Column("pairingId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("partnerUid", new TableInfo.Column("partnerUid", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("partnerName", new TableInfo.Column("partnerName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("canApproveAfterUtc", new TableInfo.Column("canApproveAfterUtc", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("removalPending", new TableInfo.Column("removalPending", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("removalEffectiveAtUtc", new TableInfo.Column("removalEffectiveAtUtc", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPartners.put("syncedAt", new TableInfo.Column("syncedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPartners = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPartners = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPartners = new TableInfo("partners", _columnsPartners, _foreignKeysPartners, _indicesPartners);
        final TableInfo _existingPartners = TableInfo.read(db, "partners");
        if (!_infoPartners.equals(_existingPartners)) {
          return new RoomOpenHelper.ValidationResult(false, "partners(com.beoffline.app.data.model.Partner).\n"
                  + " Expected:\n" + _infoPartners + "\n"
                  + " Found:\n" + _existingPartners);
        }
        final HashMap<String, TableInfo.Column> _columnsUnlockRequestCache = new HashMap<String, TableInfo.Column>(9);
        _columnsUnlockRequestCache.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("direction", new TableInfo.Column("direction", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("packageName", new TableInfo.Column("packageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("appLabel", new TableInfo.Column("appLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("requesterName", new TableInfo.Column("requesterName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("requestedAtUtc", new TableInfo.Column("requestedAtUtc", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("expiresAtUtc", new TableInfo.Column("expiresAtUtc", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUnlockRequestCache.put("grantedUntilUtc", new TableInfo.Column("grantedUntilUtc", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUnlockRequestCache = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUnlockRequestCache = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUnlockRequestCache = new TableInfo("unlock_request_cache", _columnsUnlockRequestCache, _foreignKeysUnlockRequestCache, _indicesUnlockRequestCache);
        final TableInfo _existingUnlockRequestCache = TableInfo.read(db, "unlock_request_cache");
        if (!_infoUnlockRequestCache.equals(_existingUnlockRequestCache)) {
          return new RoomOpenHelper.ValidationResult(false, "unlock_request_cache(com.beoffline.app.data.model.CachedUnlockRequest).\n"
                  + " Expected:\n" + _infoUnlockRequestCache + "\n"
                  + " Found:\n" + _existingUnlockRequestCache);
        }
        final HashMap<String, TableInfo.Column> _columnsOutboxItems = new HashMap<String, TableInfo.Column>(6);
        _columnsOutboxItems.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutboxItems.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutboxItems.put("clientKey", new TableInfo.Column("clientKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutboxItems.put("payloadJson", new TableInfo.Column("payloadJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutboxItems.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOutboxItems.put("attempts", new TableInfo.Column("attempts", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOutboxItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOutboxItems = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoOutboxItems = new TableInfo("outbox_items", _columnsOutboxItems, _foreignKeysOutboxItems, _indicesOutboxItems);
        final TableInfo _existingOutboxItems = TableInfo.read(db, "outbox_items");
        if (!_infoOutboxItems.equals(_existingOutboxItems)) {
          return new RoomOpenHelper.ValidationResult(false, "outbox_items(com.beoffline.app.data.model.OutboxItem).\n"
                  + " Expected:\n" + _infoOutboxItems + "\n"
                  + " Found:\n" + _existingOutboxItems);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "6845c011f682bfcddcbd41ec92a10ac5", "8143ae71b8df9f10a59d7229c1b2a2dc");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "block_rules","open_block_rules","allowances","solo_teaser_state","partners","unlock_request_cache","outbox_items");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `block_rules`");
      _db.execSQL("DELETE FROM `open_block_rules`");
      _db.execSQL("DELETE FROM `allowances`");
      _db.execSQL("DELETE FROM `solo_teaser_state`");
      _db.execSQL("DELETE FROM `partners`");
      _db.execSQL("DELETE FROM `unlock_request_cache`");
      _db.execSQL("DELETE FROM `outbox_items`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(BlockRuleDao.class, BlockRuleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(OpenBlockRuleDao.class, OpenBlockRuleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AllowanceDao.class, AllowanceDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SoloTeaserStateDao.class, SoloTeaserStateDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PartnerDao.class, PartnerDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(UnlockRequestCacheDao.class, UnlockRequestCacheDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(OutboxDao.class, OutboxDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public BlockRuleDao blockRuleDao() {
    if (_blockRuleDao != null) {
      return _blockRuleDao;
    } else {
      synchronized(this) {
        if(_blockRuleDao == null) {
          _blockRuleDao = new BlockRuleDao_Impl(this);
        }
        return _blockRuleDao;
      }
    }
  }

  @Override
  public OpenBlockRuleDao openBlockRuleDao() {
    if (_openBlockRuleDao != null) {
      return _openBlockRuleDao;
    } else {
      synchronized(this) {
        if(_openBlockRuleDao == null) {
          _openBlockRuleDao = new OpenBlockRuleDao_Impl(this);
        }
        return _openBlockRuleDao;
      }
    }
  }

  @Override
  public AllowanceDao allowanceDao() {
    if (_allowanceDao != null) {
      return _allowanceDao;
    } else {
      synchronized(this) {
        if(_allowanceDao == null) {
          _allowanceDao = new AllowanceDao_Impl(this);
        }
        return _allowanceDao;
      }
    }
  }

  @Override
  public SoloTeaserStateDao soloTeaserStateDao() {
    if (_soloTeaserStateDao != null) {
      return _soloTeaserStateDao;
    } else {
      synchronized(this) {
        if(_soloTeaserStateDao == null) {
          _soloTeaserStateDao = new SoloTeaserStateDao_Impl(this);
        }
        return _soloTeaserStateDao;
      }
    }
  }

  @Override
  public PartnerDao partnerDao() {
    if (_partnerDao != null) {
      return _partnerDao;
    } else {
      synchronized(this) {
        if(_partnerDao == null) {
          _partnerDao = new PartnerDao_Impl(this);
        }
        return _partnerDao;
      }
    }
  }

  @Override
  public UnlockRequestCacheDao unlockRequestCacheDao() {
    if (_unlockRequestCacheDao != null) {
      return _unlockRequestCacheDao;
    } else {
      synchronized(this) {
        if(_unlockRequestCacheDao == null) {
          _unlockRequestCacheDao = new UnlockRequestCacheDao_Impl(this);
        }
        return _unlockRequestCacheDao;
      }
    }
  }

  @Override
  public OutboxDao outboxDao() {
    if (_outboxDao != null) {
      return _outboxDao;
    } else {
      synchronized(this) {
        if(_outboxDao == null) {
          _outboxDao = new OutboxDao_Impl(this);
        }
        return _outboxDao;
      }
    }
  }
}
