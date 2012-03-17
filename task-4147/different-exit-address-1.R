library(ggplot2)
d <- read.csv("out/different-exit-address.csv", stringsAsFactors = FALSE)
d <- data.frame(
  date = as.Date(as.POSIXct(d$timestamp, origin = "1970-01-01 00:00:00")),
  differentaddress = d$differentaddress,
  writtenbytes = d$writtenbytes)
d <- aggregate(list(writtenbytes = d$writtenbytes),
  by = list(date = d$date, differentaddress = d$differentaddress),
  FUN = sum)
write.csv(d, file = "different-exit-address-aggregate.csv", quote = FALSE,
  row.names = FALSE)

