.class public Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;
.super Ljava/lang/Object;
.method public join(Z)V
    .locals 1
    if-eqz p1, :object_path
    const/4 v0, 0x1
    goto :join
:object_path
    const-string v0, "object"
:join
    invoke-static {v0}, Lapp/morphe/extension/TestHooks;->consume(Ljava/lang/Object;)V
    return-void
.end method
