/*
 *  Copyright (c) 2017-2019, bruce.ge.
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU General Public License
 *    as published by the Free Software Foundation; version 2 of
 *    the License.
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *    GNU General Public License for more details.
 *    You should have received a copy of the GNU General Public License
 *    along with this program;
 */

package com.github.erlanp.utils;

import com.intellij.openapi.ui.MessageDialogBuilder;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @Author xusong
 * @Date 2023/1/11
 * @Description
 */
public class ErrorUtils {
    public static void showError(Exception exp) {
        // 写入剪切板
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();

        StringWriter stringWriter = new StringWriter();
        exp.printStackTrace(new PrintWriter(stringWriter));
        clip.setContents(new StringSelection(stringWriter.toString()), null);
        // 提示
        MessageDialogBuilder.yesNo("Generate UT error", "Generate UT error, Error Message copied to clipboard").show();
    }
}
