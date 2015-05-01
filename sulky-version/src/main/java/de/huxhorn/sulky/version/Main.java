/*
 * sulky-modules - several general-purpose modules.
 * Copyright (C) 2007-2015 Joern Huxhorn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Copyright 2007-2015 Joern Huxhorn
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

package de.huxhorn.sulky.version;

import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;
import javax.swing.JOptionPane;

public class Main
{
	public static final String PROPERTIES_NAME_PROPERTY = "main.properties.name";
	public static final String PROPERTIES_NAME = "/main.properties";

	public static final String MAIN_CLASS_KEY = "main.class";
	public static final String REQUIRED_JAVA_VERSION_KEY = "java.version";
	public static final String SYSTEM_EXIT_KEY = "system.exit";
	public static final String SHOW_ERROR_DIALOG_KEY = "show.error.dialog";
	public static final String UNKNOWN_VERSION_FAIL_KEY = "unknown.version.fail";

	public static final int NO_ERROR_STATUS_CODE = 0;
	public static final int MAIN_CAUSED_EXCEPTION_STATUS_CODE = 1;
	public static final int MISSING_PROPERTIES_STATUS_CODE = 2;
	public static final int LOADING_PROPERTIES_FAILED_STATUS_CODE = 3;
	public static final int MALFORMED_PROPERTIES_STATUS_CODE = 4;
	public static final int MISSING_REQUIRED_JAVA_VERSION_STATUS_CODE = 5;
	public static final int INVALID_REQUIRED_JAVA_VERSION_STATUS_CODE = 6;
	public static final int MISSING_MAIN_CLASS_STATUS_CODE = 7;
	public static final int FAILED_TO_RESOLVE_CLASS_STATUS_CODE = 8;
	public static final int FAILED_TO_RESOLVE_METHOD_STATUS_CODE = 9;
	public static final int ILLEGAL_ACCESS_STATUS_CODE = 10;

	public static final int VERSION_MISMATCH_STATUS_CODE = 42;
	public static final int UNKNOWN_VERSION_STATUS_CODE = 17;

	private static boolean executingSystemExit;
	private static int statusCode;
	private static boolean showingDialog;

	public static void main(String[] args)
	{
		statusCode = NO_ERROR_STATUS_CODE;
		String propertiesName = System.getProperty(PROPERTIES_NAME_PROPERTY, PROPERTIES_NAME);

		InputStream input = Main.class.getResourceAsStream(propertiesName);
		if(input == null)
		{
			System.err.println("Failed to resolve properties from "+propertiesName+"!");
			exit(MISSING_PROPERTIES_STATUS_CODE);
			return;
		}
		Properties properties = new Properties();
		try
		{
			properties.load(input);
		}
		catch (IOException e)
		{
			System.err.println("Failed to load properties from "+propertiesName+"!");
			e.printStackTrace();
			exit(LOADING_PROPERTIES_FAILED_STATUS_CODE);
			return;
		}
		catch (IllegalArgumentException e)
		{
			System.err.println("Failed to load properties from "+propertiesName+"!");
			e.printStackTrace();
			exit(MALFORMED_PROPERTIES_STATUS_CODE);
			return;
		}

		boolean failingOnUnknownVersion = Boolean.parseBoolean(properties.getProperty(UNKNOWN_VERSION_FAIL_KEY, "false"));
		executingSystemExit = Boolean.parseBoolean(properties.getProperty(SYSTEM_EXIT_KEY, "true"));
		showingDialog = Boolean.parseBoolean(properties.getProperty(SHOW_ERROR_DIALOG_KEY, "true"));

		boolean unknownVersion = false;
		if(JavaVersion.JVM.equals(JavaVersion.MIN_VALUE))
		{
			unknownVersion = true;
			if(failingOnUnknownVersion)
			{
				System.err.println("Failed to resolve system java version!");
				exit(UNKNOWN_VERSION_STATUS_CODE);
				return;
			}
		}
		String requiredJavaVersionString = properties.getProperty(REQUIRED_JAVA_VERSION_KEY);
		if(requiredJavaVersionString == null)
		{
			System.err.println("Failed to resolve "+REQUIRED_JAVA_VERSION_KEY+" property from "+propertiesName+"!");
			exit(MISSING_REQUIRED_JAVA_VERSION_STATUS_CODE);
			return;
		}
		JavaVersion requiredJavaVersion;
		try
		{
			requiredJavaVersion = JavaVersion.parse(requiredJavaVersionString);
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
			exit(INVALID_REQUIRED_JAVA_VERSION_STATUS_CODE);
			return;
		}

		if(!unknownVersion && !JavaVersion.isAtLeast(requiredJavaVersion))
		{
			showVersionWarning(requiredJavaVersion);
			exit(VERSION_MISMATCH_STATUS_CODE);
			return;
		}

		String mainClassName = properties.getProperty(MAIN_CLASS_KEY);
		if(mainClassName == null)
		{
			System.err.println("Failed to resolve "+MAIN_CLASS_KEY+" property from "+propertiesName+"!");
			exit(MISSING_MAIN_CLASS_STATUS_CODE);
			return;
		}
		Class<?> mainClass;
		try
		{
			mainClass = Class.forName(mainClassName);
		}
		catch (Throwable e)
		{
			System.err.println("Failed to resolve class "+mainClassName+"!");
			e.printStackTrace();
			exit(FAILED_TO_RESOLVE_CLASS_STATUS_CODE);
			return;
		}

		Method mainMethod;
		try
		{
			mainMethod = mainClass.getMethod("main", String[].class);
		}
		catch (Throwable e)
		{
			System.err.println("Failed to resolve main method in class "+mainClass+"!");
			e.printStackTrace();
			exit(FAILED_TO_RESOLVE_METHOD_STATUS_CODE);
			return;
		}
		try
		{
			mainMethod.invoke(null, (Object)args);
		}
		catch (IllegalAccessException e)
		{
			System.err.println("Failed to invoke main method of class "+mainClass+"!");
			e.printStackTrace();
			exit(ILLEGAL_ACCESS_STATUS_CODE);
		}
		catch (InvocationTargetException e)
		{
			Throwable cause = e.getCause();
			if(cause != null)
			{
				cause.printStackTrace();
			}
			else
			{
				e.printStackTrace();
			}
			exit(MAIN_CAUSED_EXCEPTION_STATUS_CODE);
		}
	}

	public static int getStatusCode()
	{
		return statusCode;
	}

	private static void exit(int status)
	{
		if(executingSystemExit)
		{
			System.exit(status);
		}
		statusCode = status;
	}

	private static void showVersionWarning(JavaVersion requiredJavaVersion)
	{
		final String msg="This application requires Java "
				+ requiredJavaVersion.toVersionString()
				+ " but JVM is "
				+ JavaVersion.JVM.toVersionString()
				+ "!\nPlease upgrade your Java version.";

		if(showingDialog)
		{
			try
			{
				//noinspection Convert2Lambda
				EventQueue.invokeAndWait(new Runnable()
				{
					@Override
					public void run()
					{
						JOptionPane.showMessageDialog(null, msg, "Java Version Mismatch", JOptionPane.ERROR_MESSAGE);
					}
				});
				return;
			}
			catch (Throwable t)
			{
				// ignore
			}
		}
		System.err.println(msg);
	}
}
