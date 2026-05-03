/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.btrace.core.comm.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.DisconnectCommand;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.EventCommand;
import io.btrace.core.comm.ExitCommand;
import io.btrace.core.comm.GridDataCommand;
import io.btrace.core.comm.InstrumentCommand;
import io.btrace.core.comm.ListFailedExtensionsCommand;
import io.btrace.core.comm.ListProbesCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.comm.NumberDataCommand;
import io.btrace.core.comm.NumberMapDataCommand;
import io.btrace.core.comm.ReconnectCommand;
import io.btrace.core.comm.RenameCommand;
import io.btrace.core.comm.RetransformClassNotification;
import io.btrace.core.comm.RetransformationStartNotification;
import io.btrace.core.comm.SetSettingsCommand;
import io.btrace.core.comm.StatusCommand;
import io.btrace.core.comm.StringMapDataCommand;

/**
 * Adapter for converting between binary commands and original commands. This helps with incremental
 * migration to the new protocol without breaking compatibility.
 */
public class CommandAdapter {
  private CommandAdapter() {}

  /** Convert a binary command to an original command */
  public static Command toBtraceCommand(BinaryCommand binaryCmd) {
    if (binaryCmd == null) {
      return Command.NULL;
    }

    switch (binaryCmd.getType()) {
      case BinaryCommand.EXIT:
        BinaryExitCommand exitCmd = (BinaryExitCommand) binaryCmd;
        return new ExitCommand(exitCmd.getExitCode());

      case BinaryCommand.EVENT:
        BinaryEventCommand eventCmd = (BinaryEventCommand) binaryCmd;
        return new EventCommand(eventCmd.getEvent());

      case BinaryCommand.MESSAGE:
        BinaryMessageCommand msgCmd = (BinaryMessageCommand) binaryCmd;
        return new MessageCommand(msgCmd.getTimestamp(), msgCmd.getMessage(), msgCmd.isUrgent());

      case BinaryCommand.INSTRUMENT:
        BinaryInstrumentCommand instrCmd = (BinaryInstrumentCommand) binaryCmd;
        return new InstrumentCommand(instrCmd.getCode(), instrCmd.getArguments());

      case BinaryCommand.ERROR:
        BinaryErrorCommand errCmd = (BinaryErrorCommand) binaryCmd;
        Throwable errorCause = null;
        if (errCmd.getExceptionClass() != null
            || errCmd.getMessage() != null
            || errCmd.getStackTrace() != null) {
          errorCause =
              new RemoteException(
                  errCmd.getExceptionClass(), errCmd.getMessage(), errCmd.getStackTrace());
        }
        return new ErrorCommand(errorCause);

      case BinaryCommand.RENAME:
        BinaryRenameCommand renameCmd = (BinaryRenameCommand) binaryCmd;
        return new RenameCommand(renameCmd.getNewName());

      case BinaryCommand.STATUS:
        BinaryStatusCommand statusCmd = (BinaryStatusCommand) binaryCmd;
        int flag = statusCmd.getFlag();
        return new StatusCommand(statusCmd.isSuccess() ? flag : -flag);

      case BinaryCommand.NUMBER_MAP:
        BinaryNumberMapDataCommand numMapCmd = (BinaryNumberMapDataCommand) binaryCmd;
        return new NumberMapDataCommand(numMapCmd.getName(), numMapCmd.getData());

      case BinaryCommand.STRING_MAP:
        BinaryStringMapDataCommand strMapCmd = (BinaryStringMapDataCommand) binaryCmd;
        return new StringMapDataCommand(strMapCmd.getName(), strMapCmd.getData());

      case BinaryCommand.NUMBER:
        BinaryNumberDataCommand numCmd = (BinaryNumberDataCommand) binaryCmd;
        return new NumberDataCommand(numCmd.getName(), numCmd.getValue());

      case BinaryCommand.GRID_DATA:
        BinaryGridDataCommand gridCmd = (BinaryGridDataCommand) binaryCmd;
        GridDataCommand cmd =
            new GridDataCommand(gridCmd.getName(), gridCmd.getColumnNames(), gridCmd.getData());
        return cmd;

      case BinaryCommand.RETRANSFORMATION_START:
        BinaryRetransformationStartNotification retransStartCmd =
            (BinaryRetransformationStartNotification) binaryCmd;
        return new RetransformationStartNotification(retransStartCmd.getNumClasses());

      case BinaryCommand.RETRANSFORM_CLASS:
        BinaryRetransformClassNotification retransClassCmd =
            (BinaryRetransformClassNotification) binaryCmd;
        return new RetransformClassNotification(retransClassCmd.getClassName());

      case BinaryCommand.SET_PARAMS:
        BinarySetSettingsCommand setParamsCmd = (BinarySetSettingsCommand) binaryCmd;
        return new SetSettingsCommand(setParamsCmd.getParams());

      case BinaryCommand.LIST_PROBES:
        BinaryListProbesCommand listProbesCmd = (BinaryListProbesCommand) binaryCmd;
        ListProbesCommand listCmd = new ListProbesCommand();
        listCmd.setProbes(listProbesCmd.getProbes());
        return listCmd;

      case BinaryCommand.LIST_FAILED_EXTENSIONS:
        BinaryListFailedExtensionsCommand listFailedCmd =
            (BinaryListFailedExtensionsCommand) binaryCmd;
        ListFailedExtensionsCommand failedCmd = new ListFailedExtensionsCommand();
        failedCmd.setFailedExtensionsList(listFailedCmd.getFailures());
        return failedCmd;

      case BinaryCommand.DISCONNECT:
        BinaryDisconnectCommand disconnectCmd = (BinaryDisconnectCommand) binaryCmd;
        return new DisconnectCommand(disconnectCmd.getProbeId());

      case BinaryCommand.RECONNECT:
        BinaryReconnectCommand reconnectCmd = (BinaryReconnectCommand) binaryCmd;
        return new ReconnectCommand(reconnectCmd.getProbeId());

      default:
        throw new IllegalArgumentException(
            "Unsupported command type for conversion: " + binaryCmd.getType());
    }
  }

  /** Convert an original command to a binary command */
  public static BinaryCommand toBinaryCommand(Command originalCmd) {
    if (originalCmd == Command.NULL) {
      return null;
    }

    switch (originalCmd.getType()) {
      case Command.EXIT:
        ExitCommand exitCmd = (ExitCommand) originalCmd;
        return new BinaryExitCommand(exitCmd.getExitCode());

      case Command.EVENT:
        EventCommand eventCmd = (EventCommand) originalCmd;
        return new BinaryEventCommand(eventCmd.getEvent());

      case Command.MESSAGE:
        MessageCommand msgCmd = (MessageCommand) originalCmd;
        return new BinaryMessageCommand(
            msgCmd.getTime(), msgCmd.getMessage(), originalCmd.isUrgent());

      case Command.INSTRUMENT:
        InstrumentCommand instrCmd = (InstrumentCommand) originalCmd;
        return new BinaryInstrumentCommand(instrCmd.getCode(), instrCmd.getArguments());

      case Command.ERROR:
        ErrorCommand errCmd = (ErrorCommand) originalCmd;
        Throwable cause = errCmd.getCause();
        String exceptionClass = null;
        String message = null;
        String stackTrace = null;
        if (cause != null) {
          exceptionClass = cause.getClass().getName();
          message = cause.getMessage();
          StringWriter writer = new StringWriter();
          PrintWriter printWriter = new PrintWriter(writer);
          cause.printStackTrace(printWriter);
          printWriter.flush();
          stackTrace = writer.toString();
        }
        return new BinaryErrorCommand(exceptionClass, message, stackTrace);

      case Command.RENAME:
        RenameCommand renameCmd = (RenameCommand) originalCmd;
        return new BinaryRenameCommand(renameCmd.getNewName());

      case Command.STATUS:
        StatusCommand statusCmd = (StatusCommand) originalCmd;
        return new BinaryStatusCommand(statusCmd.getFlag(), statusCmd.isSuccess());

      case Command.NUMBER_MAP:
        NumberMapDataCommand numMapCmd = (NumberMapDataCommand) originalCmd;
        Map<String, Number> numMap = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : numMapCmd.getData().entrySet()) {
          if (entry.getValue() instanceof Number) {
            numMap.put(entry.getKey(), (Number) entry.getValue());
          }
        }
        return new BinaryNumberMapDataCommand(numMapCmd.getName(), numMap);

      case Command.STRING_MAP:
        StringMapDataCommand strMapCmd = (StringMapDataCommand) originalCmd;
        Map<String, String> strMap = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : strMapCmd.getData().entrySet()) {
          strMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new BinaryStringMapDataCommand(strMapCmd.getName(), strMap);

      case Command.NUMBER:
        NumberDataCommand numCmd = (NumberDataCommand) originalCmd;
        return new BinaryNumberDataCommand(numCmd.getName(), numCmd.getValue());

      case Command.GRID_DATA:
        GridDataCommand gridCmd = (GridDataCommand) originalCmd;
        return new BinaryGridDataCommand(
            gridCmd.getName(), gridCmd.getColumnNames(), gridCmd.getData());

      case Command.RETRANSFORMATION_START:
        RetransformationStartNotification retransStartCmd =
            (RetransformationStartNotification) originalCmd;
        return new BinaryRetransformationStartNotification(retransStartCmd.getNumClasses());

      case Command.RETRANSFORM_CLASS:
        RetransformClassNotification retransClassCmd = (RetransformClassNotification) originalCmd;
        return new BinaryRetransformClassNotification(retransClassCmd.getClassName(), 0, 0);

      case Command.SET_PARAMS:
        SetSettingsCommand setParamsCmd = (SetSettingsCommand) originalCmd;
        return new BinarySetSettingsCommand(setParamsCmd.getParams());

      case Command.LIST_PROBES:
        ListProbesCommand listProbesCmd = (ListProbesCommand) originalCmd;
        BinaryListProbesCommand listCmd = new BinaryListProbesCommand();
        listCmd.setProbes(listProbesCmd.getProbes());
        return listCmd;

      case Command.DISCONNECT:
        DisconnectCommand disconnectCmd = (DisconnectCommand) originalCmd;
        return new BinaryDisconnectCommand(disconnectCmd.getProbeId());

      case Command.RECONNECT:
        ReconnectCommand reconnectCmd = (ReconnectCommand) originalCmd;
        return new BinaryReconnectCommand(reconnectCmd.getProbeId());

      case Command.LIST_FAILED_EXTENSIONS:
        ListFailedExtensionsCommand listFailedCmd = (ListFailedExtensionsCommand) originalCmd;
        BinaryListFailedExtensionsCommand binaryFailedCmd = new BinaryListFailedExtensionsCommand();
        binaryFailedCmd.setFailures(listFailedCmd.getFailedExtensions());
        return binaryFailedCmd;

      default:
        throw new IllegalArgumentException(
            "Unsupported command type for conversion: " + originalCmd.getType());
    }
  }
}
