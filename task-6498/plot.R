# uncomment for 2+ years
#CUTOFF <- "2010-05-26"
#DETAIL <- "week"
CUTOFF <- "2012-06-02"
DETAIL <- "day"

require(ggplot2)
require(reshape)
require(RColorBrewer)
data <- read.csv("results.csv", stringsAsFactors = FALSE)
data <- data[data$valid_after >= paste(CUTOFF, "00:00:00"), ]

r <- data
r <- r[r$min_rate == 11875 & r$ports == "80-443-554-1755" &
  r$min_advbw == 5000, ]
r <- aggregate(list(relays = r$relays, P_exit = 100 * r$exit_prob),
  by = list(date = as.Date(cut.Date(as.Date(r$valid_after), DETAIL))),
  FUN = median)
r <- melt(r, id.vars = c("date"))
r <- data.frame(r, type = ifelse(r$variable == "P_exit",
  "Total exit probability (in %)", "Number of relays"))
ggplot(r, aes(x = date, y = value)) +
geom_line(colour = "purple", size = 0.75) +
facet_grid(type ~ ., scales = "free_y") +
scale_x_date(name = "") +
scale_y_continuous(name = "") +
scale_colour_manual(values = c("purple", "orange")) +
opts(title = paste("Fast relays (95+ Mbit/s configured bandwidth rate,\n",
  "5000+ KB/s advertised bandwidth capacity,\n",
  "exit to ports 80, 443, 554, and 1755,\n",
  "at most 2 relays per /24 network)\n", sep = ""))
ggsave("fast-exits.png", width = 8, height = 6, dpi = 100)

s <- data
s <- s[s$min_rate == 11875 & s$ports == "80-443-554-1755", ]
s <- aggregate(list(relays = s$relays, P_exit = 100 * s$exit_prob),
  by = list(date = as.Date(cut.Date(as.Date(s$valid_after), DETAIL)),
  min_advbw = s$min_advbw), FUN = median)
s <- melt(s, id.vars = c("date", "min_advbw"))
s <- data.frame(s, type = ifelse(s$variable == "P_exit",
  "Total exit probability (in %)", "Number of relays"))
ggplot(s, aes(x = date, y = value, colour = as.factor(min_advbw))) +
geom_line() +
facet_grid(type ~ ., scales = "free_y") +
scale_x_date(name = "") +
scale_y_continuous(name = "") +
scale_colour_manual(name = "Advertised bandwidth (in KB/s)",
  values = brewer.pal(6, "Purples")[2:6]) +
opts(title = paste("Relays meeting the fast-exit requirements\nwith",
  "varying minimum advertised bandwidth capacities"),
  legend.position = "top")
ggsave("advertised-bandwidths-requirement.png", width = 8, height = 6,
  dpi = 100)

t <- data
t1 <- t[t$min_rate == 11875 & t$ports == "80-443-554-1755" &
  t$min_advbw == 5000, ]
t2 <- t[t$min_rate == 10000 & t$ports == "80-443" &
  t$min_advbw == 2000, ]
t <- rbind(data.frame(t1, var = "95+ Mbit/s, 5000+ KB/s, 80/443/554/1755"),
  data.frame(t2, var = "80+ Mbit/s, 2000+ KB/s, 80/443"))
t <- aggregate(list(relays = t$relays, P_exit = 100 * t$exit_prob),
  by = list(date = as.Date(cut.Date(as.Date(t$valid_after), DETAIL)),
  var = t$var), FUN = median)
t <- melt(t, id.vars = c("date", "var"))
t <- data.frame(t, type = ifelse(t$variable == "P_exit",
  "Total exit probability (in %)", "Number of relays"))
ggplot(t, aes(x = date, y = value, colour = var)) +
geom_line(size = 0.75) +
facet_grid(type ~ ., scales = "free_y") +
scale_x_date(name = "") +
scale_y_continuous(name = "") +
scale_colour_manual(name = "", values = c("purple", "orange")) +
opts(title = "Relays almost meeting the fast-exit requirements",
  legend.position = "top")
ggsave("almost-fast-exits.png", width = 8, height = 6, dpi = 100)

