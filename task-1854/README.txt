Basic algorithm - 
1) find probability of each relay in pristine consensus
2) find probability of each relay in modified consensus
3) for every relay in modified consensus,
      calculate prob_diff where
      prob_diff = prob_in_pristine_consensus[relay] - prob_in_modified_consensus[relay]
4) find largest prob_diff
5) remove the relays with lowest adv_bw
6) go to step 2
