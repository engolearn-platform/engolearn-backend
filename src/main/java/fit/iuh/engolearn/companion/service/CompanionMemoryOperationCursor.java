package fit.iuh.engolearn.companion.service;

public final class CompanionMemoryOperationCursor {
    private int ordinal;
    private boolean mutationsSuppressed;

    public int ordinal() { return ordinal; }
    public boolean mutationsSuppressed() { return mutationsSuppressed; }
    public void advance() { ordinal++; }
    public void suppressMutations() { mutationsSuppressed = true; }
}
