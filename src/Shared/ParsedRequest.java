package Shared;

public class ParsedRequest {

    private final Command  command;
    private final String[] params;

    public ParsedRequest(Command command, String[] params) {
        this.command = command;
        this.params  = params;
    }

    public Command getCommand() {
        return command;
    }

    public String[] getParams() {
        return params;
    }

    public String getParam(int index) {
        if (index < 0 || index >= params.length) {
            return "";
        }
        return params[index];
    }

    public int getParamCount() {
        return params.length;
    }

    @Override
    public String toString() {
        return "ParsedRequest{command=" + command + ", params=" + java.util.Arrays.toString(params) + "}";
    }
}
