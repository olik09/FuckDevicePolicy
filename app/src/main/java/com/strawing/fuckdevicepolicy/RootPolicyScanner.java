package com.strawing.fuckdevicepolicy;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public final class RootPolicyScanner {

    private RootPolicyScanner() {}

    public static String dump() {
        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("dumpsys device_policy\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }

            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errOutput = new StringBuilder();
            while ((line = errReader.readLine()) != null) {
                errOutput.append(line).append('\n');
            }

            process.waitFor();

            if (output.length() == 0 && errOutput.length() > 0) {
                return "Error requesting root or running dumpsys:\n" + errOutput;
            }
        } catch (Exception e) {
            return "Failed to run root command: " + e.getMessage()
                    + "\n(Is the device rooted, and did you grant root access to this app?)";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return output.toString();
    }

    public static boolean hasRootAccess() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean rootConfirmed = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("uid=0")) {
                    rootConfirmed = true;
                }
            }
            process.waitFor();
            return rootConfirmed;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
