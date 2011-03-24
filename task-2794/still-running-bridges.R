library(ggplot2)
anyip <- read.csv("still-running-bridges.csv", stringsAsFactors = FALSE)
sameip <- anyip[anyip$addresschange == FALSE, ]
data <- rbind(
  data.frame(status = anyip$status, bridgeid = anyip$anyid, address = "any IP address"),
  data.frame(status = sameip$status, bridgeid = sameip$sameid, address = "same IP address"))
ggplot(data, aes(x = as.POSIXct(status),
  y = (max(data$bridgeid) - bridgeid) / max(data$bridgeid))) +
facet_grid(address ~ .) +
geom_point(size = 0.2, colour = "springgreen3") +
scale_x_datetime(name = "") +
scale_y_continuous(name = "", formatter = "percent") +
opts(title = "Uptimes of bridges that were running Jan 2, 2011, 00:00:00 UTC\n")
ggsave(filename = "still-running-bridges.png", width = 8, height = 5, dpi = 72)

