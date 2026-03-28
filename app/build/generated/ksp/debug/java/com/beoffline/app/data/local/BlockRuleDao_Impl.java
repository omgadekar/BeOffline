package com.beoffline.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.beoffline.app.data.model.BlockRule;
import com.beoffline.app.data.model.RuleType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BlockRuleDao_Impl implements BlockRuleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BlockRule> __insertionAdapterOfBlockRule;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<BlockRule> __deletionAdapterOfBlockRule;

  private final EntityDeletionOrUpdateAdapter<BlockRule> __updateAdapterOfBlockRule;

  private final SharedSQLiteStatement __preparedStmtOfDeleteRuleById;

  private final SharedSQLiteStatement __preparedStmtOfSetRuleActive;

  private final SharedSQLiteStatement __preparedStmtOfDeactivateAllRules;

  private final SharedSQLiteStatement __preparedStmtOfSetTimerStartedAt;

  public BlockRuleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBlockRule = new EntityInsertionAdapter<BlockRule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `block_rules` (`id`,`name`,`blockedPackages`,`ruleType`,`isActive`,`startHour`,`startMinute`,`endHour`,`endMinute`,`activeDays`,`timerDurationMinutes`,`timerStartedAt`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BlockRule entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final String _tmp = __converters.fromStringList(entity.getBlockedPackages());
        statement.bindString(3, _tmp);
        statement.bindString(4, __RuleType_enumToString(entity.getRuleType()));
        final int _tmp_1 = entity.isActive() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        if (entity.getStartHour() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getStartHour());
        }
        if (entity.getStartMinute() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getStartMinute());
        }
        if (entity.getEndHour() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getEndHour());
        }
        if (entity.getEndMinute() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getEndMinute());
        }
        final String _tmp_2 = __converters.fromIntList(entity.getActiveDays());
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_2);
        }
        if (entity.getTimerDurationMinutes() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getTimerDurationMinutes());
        }
        if (entity.getTimerStartedAt() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getTimerStartedAt());
        }
        statement.bindLong(13, entity.getCreatedAt());
      }
    };
    this.__deletionAdapterOfBlockRule = new EntityDeletionOrUpdateAdapter<BlockRule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `block_rules` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BlockRule entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfBlockRule = new EntityDeletionOrUpdateAdapter<BlockRule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `block_rules` SET `id` = ?,`name` = ?,`blockedPackages` = ?,`ruleType` = ?,`isActive` = ?,`startHour` = ?,`startMinute` = ?,`endHour` = ?,`endMinute` = ?,`activeDays` = ?,`timerDurationMinutes` = ?,`timerStartedAt` = ?,`createdAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BlockRule entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        final String _tmp = __converters.fromStringList(entity.getBlockedPackages());
        statement.bindString(3, _tmp);
        statement.bindString(4, __RuleType_enumToString(entity.getRuleType()));
        final int _tmp_1 = entity.isActive() ? 1 : 0;
        statement.bindLong(5, _tmp_1);
        if (entity.getStartHour() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getStartHour());
        }
        if (entity.getStartMinute() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getStartMinute());
        }
        if (entity.getEndHour() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getEndHour());
        }
        if (entity.getEndMinute() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getEndMinute());
        }
        final String _tmp_2 = __converters.fromIntList(entity.getActiveDays());
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_2);
        }
        if (entity.getTimerDurationMinutes() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getTimerDurationMinutes());
        }
        if (entity.getTimerStartedAt() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getTimerStartedAt());
        }
        statement.bindLong(13, entity.getCreatedAt());
        statement.bindLong(14, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteRuleById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM block_rules WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetRuleActive = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE block_rules SET isActive = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeactivateAllRules = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE block_rules SET isActive = 0";
        return _query;
      }
    };
    this.__preparedStmtOfSetTimerStartedAt = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE block_rules SET timerStartedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertRule(final BlockRule rule, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfBlockRule.insertAndReturnId(rule);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteRule(final BlockRule rule, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBlockRule.handle(rule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateRule(final BlockRule rule, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBlockRule.handle(rule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteRuleById(final int id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteRuleById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteRuleById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setRuleActive(final int id, final boolean isActive,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetRuleActive.acquire();
        int _argIndex = 1;
        final int _tmp = isActive ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetRuleActive.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deactivateAllRules(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeactivateAllRules.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeactivateAllRules.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setTimerStartedAt(final int id, final Long startedAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetTimerStartedAt.acquire();
        int _argIndex = 1;
        if (startedAt == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, startedAt);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetTimerStartedAt.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BlockRule>> getAllRules() {
    final String _sql = "SELECT * FROM block_rules ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"block_rules"}, new Callable<List<BlockRule>>() {
      @Override
      @NonNull
      public List<BlockRule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBlockedPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "blockedPackages");
          final int _cursorIndexOfRuleType = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfStartHour = CursorUtil.getColumnIndexOrThrow(_cursor, "startHour");
          final int _cursorIndexOfStartMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "startMinute");
          final int _cursorIndexOfEndHour = CursorUtil.getColumnIndexOrThrow(_cursor, "endHour");
          final int _cursorIndexOfEndMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "endMinute");
          final int _cursorIndexOfActiveDays = CursorUtil.getColumnIndexOrThrow(_cursor, "activeDays");
          final int _cursorIndexOfTimerDurationMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "timerDurationMinutes");
          final int _cursorIndexOfTimerStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "timerStartedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<BlockRule> _result = new ArrayList<BlockRule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BlockRule _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final List<String> _tmpBlockedPackages;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfBlockedPackages);
            _tmpBlockedPackages = __converters.toStringList(_tmp);
            final RuleType _tmpRuleType;
            _tmpRuleType = __RuleType_stringToEnum(_cursor.getString(_cursorIndexOfRuleType));
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final Integer _tmpStartHour;
            if (_cursor.isNull(_cursorIndexOfStartHour)) {
              _tmpStartHour = null;
            } else {
              _tmpStartHour = _cursor.getInt(_cursorIndexOfStartHour);
            }
            final Integer _tmpStartMinute;
            if (_cursor.isNull(_cursorIndexOfStartMinute)) {
              _tmpStartMinute = null;
            } else {
              _tmpStartMinute = _cursor.getInt(_cursorIndexOfStartMinute);
            }
            final Integer _tmpEndHour;
            if (_cursor.isNull(_cursorIndexOfEndHour)) {
              _tmpEndHour = null;
            } else {
              _tmpEndHour = _cursor.getInt(_cursorIndexOfEndHour);
            }
            final Integer _tmpEndMinute;
            if (_cursor.isNull(_cursorIndexOfEndMinute)) {
              _tmpEndMinute = null;
            } else {
              _tmpEndMinute = _cursor.getInt(_cursorIndexOfEndMinute);
            }
            final List<Integer> _tmpActiveDays;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfActiveDays)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfActiveDays);
            }
            if (_tmp_2 == null) {
              _tmpActiveDays = null;
            } else {
              _tmpActiveDays = __converters.toIntList(_tmp_2);
            }
            final Integer _tmpTimerDurationMinutes;
            if (_cursor.isNull(_cursorIndexOfTimerDurationMinutes)) {
              _tmpTimerDurationMinutes = null;
            } else {
              _tmpTimerDurationMinutes = _cursor.getInt(_cursorIndexOfTimerDurationMinutes);
            }
            final Long _tmpTimerStartedAt;
            if (_cursor.isNull(_cursorIndexOfTimerStartedAt)) {
              _tmpTimerStartedAt = null;
            } else {
              _tmpTimerStartedAt = _cursor.getLong(_cursorIndexOfTimerStartedAt);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new BlockRule(_tmpId,_tmpName,_tmpBlockedPackages,_tmpRuleType,_tmpIsActive,_tmpStartHour,_tmpStartMinute,_tmpEndHour,_tmpEndMinute,_tmpActiveDays,_tmpTimerDurationMinutes,_tmpTimerStartedAt,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<BlockRule>> getActiveRules() {
    final String _sql = "SELECT * FROM block_rules WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"block_rules"}, new Callable<List<BlockRule>>() {
      @Override
      @NonNull
      public List<BlockRule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBlockedPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "blockedPackages");
          final int _cursorIndexOfRuleType = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfStartHour = CursorUtil.getColumnIndexOrThrow(_cursor, "startHour");
          final int _cursorIndexOfStartMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "startMinute");
          final int _cursorIndexOfEndHour = CursorUtil.getColumnIndexOrThrow(_cursor, "endHour");
          final int _cursorIndexOfEndMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "endMinute");
          final int _cursorIndexOfActiveDays = CursorUtil.getColumnIndexOrThrow(_cursor, "activeDays");
          final int _cursorIndexOfTimerDurationMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "timerDurationMinutes");
          final int _cursorIndexOfTimerStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "timerStartedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final List<BlockRule> _result = new ArrayList<BlockRule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BlockRule _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final List<String> _tmpBlockedPackages;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfBlockedPackages);
            _tmpBlockedPackages = __converters.toStringList(_tmp);
            final RuleType _tmpRuleType;
            _tmpRuleType = __RuleType_stringToEnum(_cursor.getString(_cursorIndexOfRuleType));
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final Integer _tmpStartHour;
            if (_cursor.isNull(_cursorIndexOfStartHour)) {
              _tmpStartHour = null;
            } else {
              _tmpStartHour = _cursor.getInt(_cursorIndexOfStartHour);
            }
            final Integer _tmpStartMinute;
            if (_cursor.isNull(_cursorIndexOfStartMinute)) {
              _tmpStartMinute = null;
            } else {
              _tmpStartMinute = _cursor.getInt(_cursorIndexOfStartMinute);
            }
            final Integer _tmpEndHour;
            if (_cursor.isNull(_cursorIndexOfEndHour)) {
              _tmpEndHour = null;
            } else {
              _tmpEndHour = _cursor.getInt(_cursorIndexOfEndHour);
            }
            final Integer _tmpEndMinute;
            if (_cursor.isNull(_cursorIndexOfEndMinute)) {
              _tmpEndMinute = null;
            } else {
              _tmpEndMinute = _cursor.getInt(_cursorIndexOfEndMinute);
            }
            final List<Integer> _tmpActiveDays;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfActiveDays)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfActiveDays);
            }
            if (_tmp_2 == null) {
              _tmpActiveDays = null;
            } else {
              _tmpActiveDays = __converters.toIntList(_tmp_2);
            }
            final Integer _tmpTimerDurationMinutes;
            if (_cursor.isNull(_cursorIndexOfTimerDurationMinutes)) {
              _tmpTimerDurationMinutes = null;
            } else {
              _tmpTimerDurationMinutes = _cursor.getInt(_cursorIndexOfTimerDurationMinutes);
            }
            final Long _tmpTimerStartedAt;
            if (_cursor.isNull(_cursorIndexOfTimerStartedAt)) {
              _tmpTimerStartedAt = null;
            } else {
              _tmpTimerStartedAt = _cursor.getLong(_cursorIndexOfTimerStartedAt);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _item = new BlockRule(_tmpId,_tmpName,_tmpBlockedPackages,_tmpRuleType,_tmpIsActive,_tmpStartHour,_tmpStartMinute,_tmpEndHour,_tmpEndMinute,_tmpActiveDays,_tmpTimerDurationMinutes,_tmpTimerStartedAt,_tmpCreatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getRuleById(final int id, final Continuation<? super BlockRule> $completion) {
    final String _sql = "SELECT * FROM block_rules WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BlockRule>() {
      @Override
      @Nullable
      public BlockRule call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfBlockedPackages = CursorUtil.getColumnIndexOrThrow(_cursor, "blockedPackages");
          final int _cursorIndexOfRuleType = CursorUtil.getColumnIndexOrThrow(_cursor, "ruleType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfStartHour = CursorUtil.getColumnIndexOrThrow(_cursor, "startHour");
          final int _cursorIndexOfStartMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "startMinute");
          final int _cursorIndexOfEndHour = CursorUtil.getColumnIndexOrThrow(_cursor, "endHour");
          final int _cursorIndexOfEndMinute = CursorUtil.getColumnIndexOrThrow(_cursor, "endMinute");
          final int _cursorIndexOfActiveDays = CursorUtil.getColumnIndexOrThrow(_cursor, "activeDays");
          final int _cursorIndexOfTimerDurationMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "timerDurationMinutes");
          final int _cursorIndexOfTimerStartedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "timerStartedAt");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final BlockRule _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final List<String> _tmpBlockedPackages;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfBlockedPackages);
            _tmpBlockedPackages = __converters.toStringList(_tmp);
            final RuleType _tmpRuleType;
            _tmpRuleType = __RuleType_stringToEnum(_cursor.getString(_cursorIndexOfRuleType));
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final Integer _tmpStartHour;
            if (_cursor.isNull(_cursorIndexOfStartHour)) {
              _tmpStartHour = null;
            } else {
              _tmpStartHour = _cursor.getInt(_cursorIndexOfStartHour);
            }
            final Integer _tmpStartMinute;
            if (_cursor.isNull(_cursorIndexOfStartMinute)) {
              _tmpStartMinute = null;
            } else {
              _tmpStartMinute = _cursor.getInt(_cursorIndexOfStartMinute);
            }
            final Integer _tmpEndHour;
            if (_cursor.isNull(_cursorIndexOfEndHour)) {
              _tmpEndHour = null;
            } else {
              _tmpEndHour = _cursor.getInt(_cursorIndexOfEndHour);
            }
            final Integer _tmpEndMinute;
            if (_cursor.isNull(_cursorIndexOfEndMinute)) {
              _tmpEndMinute = null;
            } else {
              _tmpEndMinute = _cursor.getInt(_cursorIndexOfEndMinute);
            }
            final List<Integer> _tmpActiveDays;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfActiveDays)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfActiveDays);
            }
            if (_tmp_2 == null) {
              _tmpActiveDays = null;
            } else {
              _tmpActiveDays = __converters.toIntList(_tmp_2);
            }
            final Integer _tmpTimerDurationMinutes;
            if (_cursor.isNull(_cursorIndexOfTimerDurationMinutes)) {
              _tmpTimerDurationMinutes = null;
            } else {
              _tmpTimerDurationMinutes = _cursor.getInt(_cursorIndexOfTimerDurationMinutes);
            }
            final Long _tmpTimerStartedAt;
            if (_cursor.isNull(_cursorIndexOfTimerStartedAt)) {
              _tmpTimerStartedAt = null;
            } else {
              _tmpTimerStartedAt = _cursor.getLong(_cursorIndexOfTimerStartedAt);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            _result = new BlockRule(_tmpId,_tmpName,_tmpBlockedPackages,_tmpRuleType,_tmpIsActive,_tmpStartHour,_tmpStartMinute,_tmpEndHour,_tmpEndMinute,_tmpActiveDays,_tmpTimerDurationMinutes,_tmpTimerStartedAt,_tmpCreatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __RuleType_enumToString(@NonNull final RuleType _value) {
    switch (_value) {
      case PERMANENT: return "PERMANENT";
      case SCHEDULED: return "SCHEDULED";
      case TIMER: return "TIMER";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private RuleType __RuleType_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "PERMANENT": return RuleType.PERMANENT;
      case "SCHEDULED": return RuleType.SCHEDULED;
      case "TIMER": return RuleType.TIMER;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
