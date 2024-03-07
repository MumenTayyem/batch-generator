public class TaskResult<T> {
    private T _result;
    private Exception _exception;
    private boolean _ok = true;

    public T getResult() {
        return _result;
    }

    public void setResult(T _result) {
        this._result = _result;
    }

    public Exception getException() {
        return _exception;
    }

    public void setException(Exception _exception) {
        this._exception = _exception;
        this._ok = false;
    }

    public boolean ok(){
        return this._ok;
    }
}
