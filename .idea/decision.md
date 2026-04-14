Trino smart approval design discussion

A large section focused on how Trino could support smart approval.

Initial proposal
One idea was:
	•	a user query comes into Trino
	•	Trino creates a query ID
	•	authorization happens through OPA
	•	if OPA denies but indicates smart approval required
	•	then Trino/plugin submits a smart approval request
	•	the query is somehow paused/held/pending
	•	after approval, the query is re-executed or resumed with correlation to the original query

Problems with doing this inside Trino
The concerns were:
	•	it adds complexity inside Trino
	•	coordinator-side blocking may be risky
	•	holding queries inside authorization paths is tricky
	•	query lifecycle and state management become awkward
	•	returning useful user feedback is hard

John’s feedback was that if done internally, the cleanest place would be some kind of access control module/plugin, but even then it may not be the right design.

Alternative ideas discussed
A few options came up:
	•	hold the query internally while waiting for approval
	•	use a custom plugin or callback mechanism
	•	use a polymorphic table function/UDF/PTF approach
	•	kill the query and return an error/message saying smart approval is required
	•	move the logic outside Trino entirely

Final direction on Trino smart approval

The discussion increasingly converged toward:

Do not embed the full smart approval workflow inside Trino.
Instead:
	•	put a gateway/presentation/orchestration service in front of Trino
	•	that layer handles:
	•	smart approval request creation
	•	status checking
	•	approval tracking
	•	preserving user context
	•	then passing the approved request onward to Trino

This was seen as much cleaner than making Trino itself manage async approval workflows.

Why the front service is preferred

Because users or agents need feedback like:
	•	approval requested
	•	waiting for approval
	•	approval granted/denied
	•	request status/correlation

That is better handled by a service in front of Trino rather than buried inside Trino’s internal query engine.

This is especially true for:
	•	agents
	•	Galaxy
	•	any front-end or presentation-driven experience

Specific use cases mentioned

The smart approval flow is especially needed for:
	•	Agents
	•	Mobius
	•	possibly Galaxy
	•	text-to-SQL / agent-issued queries where the user intent and access need evaluation