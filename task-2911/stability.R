library(ggplot2)
data <- read.csv("stability.csv", stringsAsFactors = FALSE)

d <- data
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  mean)
d <- rbind(
  data.frame(date = d$date, value = d$running,
    variable = "Running"),
  data.frame(date = d$date, value = d$stable,
    variable = "Running + Stable"),
  data.frame(date = d$date, value = d$guard,
    variable = "Running + Guard"))
ggplot(d, aes(x = as.Date(date), y = value, colour = variable)) +
geom_line(size = 0.7) +
scale_x_date("", major = "3 months", minor = "1 month",
  format = "%b %Y") +
scale_y_continuous("Number    \nof relays    ",
  limits = c(0, max(d$value, na.rm = TRUE))) +
scale_colour_manual(name = "Assigned relay flags\n",
  values = c("Running" = "black", "Running + Stable" = "grey45",
    "Running + HSDir" = "grey", "Running + Guard" = "grey70")) +
opts(axis.title.y = theme_text(size = 12 * 0.8, face = "bold",
  vjust = 0.5, hjust = 1))
ggsave(filename = "relayflags.pdf", width = 8, height = 4, dpi = 100)

d <- data
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  mean)
d <- rbind(
  data.frame(x = d$date, y = d$stable / d$running,
    variable = "Stable (observed)"),
  data.frame(x = d$date, y = d$stable50 / d$running,
    variable = "Stable (simulated)"),
  data.frame(x = d$date, y = d$guard / d$running,
    variable = "Guard (observed)"),
  data.frame(x = d$date, y = d$guard50wfu50advbw / d$running,
    variable = "Guard (simulated)"))
d[d$x >= '2010-06-26' & d$x <= '2010-06-28', "y"] <- NA
d[d$x >= '2010-01-25' & d$x <= '2010-01-26', "y"] <- NA
ggplot(d, aes(x = x, y = y, colour = variable, linetype = variable)) +
geom_line() +
scale_y_continuous(name = "Fraction of   \nRunning relays   ",
  formatter = "percent", limits = c(0, max(d$y, na.rm = TRUE))) +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y") +
scale_colour_manual(name = "Assigned relay flags\n",
  values = c("Stable (observed)" = "grey50",
  "Stable (simulated)" = "grey50",
  "Guard (observed)" = "black",
  "Guard (simulated)" = "black")) +
scale_linetype_manual(name = "Assigned relay flags\n",
  values = c(3, 1, 3, 1)) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "default-reqs.pdf", width = 8, height = 4, dpi = 100)

d <- data
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  mean)
d <- rbind(
  data.frame(x = d$date, y = d$stableintersect,
    variable = "simulated and observed", flag = "Stable"),
  data.frame(x = d$date, y = d$stableobserved,
    variable = "only observed", flag = "Stable"),
  data.frame(x = d$date, y = d$stablesimulated,
    variable = "only simulated", flag = "Stable"),
  data.frame(x = d$date, y = d$guardintersect,
    variable = "simulated and observed", flag = "Guard"),
  data.frame(x = d$date, y = d$guardobserved,
    variable = "only observed", flag = "Guard"),
  data.frame(x = d$date, y = d$guardsimulated,
    variable = "only simulated", flag = "Guard"),
  data.frame(x = NA, y = 0, variable = "only simulated", flag = "Stable"),
  data.frame(x = NA, y = 0, variable = "only simulated", flag = "Guard"))
ggplot(d, aes(x = x, y = y, linetype = variable)) +
geom_line() +
facet_grid(flag ~ ., scale = "free_y") +
scale_y_continuous(name = "Number   \nof relays   ") +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y") +
scale_linetype_manual(name = "", values = c(4, 3, 1)) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "diff-sim-obs.pdf", width = 8, height = 4, dpi = 100)

d <- data
d_mean <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  mean)
d_max <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  max)
d_min <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  min)
d <- rbind(
  data.frame(x = d_mean$date, y = d_mean$minwmtbfa50 / (24 * 60 * 60),
    ymin = d_min$minwmtbfa50 / (24 * 60 * 60),
    ymax = d_max$minwmtbfa50 / (24 * 60 * 60),
    var = "Median Weighted Mean Time Between Failure"),
  data.frame(x = d_mean$date, y = d_mean$minwta / (24 * 60 * 60),
    ymin = d_min$minwta / (24 * 60 * 60),
    ymax = d_max$minwta / (24 * 60 * 60),
    var = "12.5th percentile Weighted Time Known"),
  data.frame(x = d_mean$date, y = d_mean$minwfua50wfu / 10000,
    ymin = d_min$minwfua50wfu / 10000,
    ymax = d_max$minwfua50wfu / 10000,
    var = "Median Weighted Fractional Uptime"),
  data.frame(x = d_mean$date, y = d_mean$minadvbwa50advbw / 1024,
    ymin = d_min$minadvbwa50advbw / 1024,
    ymax = d_max$minadvbwa50advbw / 1024,
    var = "Median Advertised Bandwidth"))
e <- data.frame(
  yintercept = c(5, 8, 0.98, 250),
  var = c("Median Weighted Mean Time Between Failure",
    "12.5th percentile Weighted Time Known",
    "Median Weighted Fractional Uptime",
    "Median Advertised Bandwidth"))
ggplot(d, aes(x = as.Date(x), y = y, ymin = ymin, ymax = ymax)) +
geom_line(colour = "grey30") +
geom_ribbon(alpha = 0.3) +
geom_hline(data = e, aes(yintercept = yintercept), colour = "gray30",
  linetype = 2) +
facet_wrap(~ var, scales = "free_y") +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y") +
scale_y_continuous(name = "")
ggsave(filename = "requirements.pdf", width = 8, height = 5, dpi = 100)

d <- data
d <- d[d$time < '2010-06-26 00:00:00' | d$time > '2010-06-28 23:00:00', ]
d <- d[d$time < '2010-01-25 00:00:00' | d$time > '2010-01-26 23:00:00', ]
d <- rbind(
  data.frame(x = sort(d$perc10tunf30) / (60 * 60),
      y = 1:length(d$perc10tunf30) / length(d$perc10tunf30),
      sim = "30th (simulated)"),
  data.frame(x = sort(d$perc10tunf) / (60 * 60),
      y = 1:length(d$perc10tunf) / length(d$perc10tunf),
      sim = "50th (observed)"),
  data.frame(x = sort(d$perc10tunf40) / (60 * 60),
      y = 1:length(d$perc10tunf40) / length(d$perc10tunf40),
      sim = "40th (simulated)"),
  data.frame(x = sort(d$perc10tunf50) / (60 * 60),
      y = 1:length(d$perc10tunf50) / length(d$perc10tunf50),
      sim = "50th (simulated)"),
  data.frame(x = sort(d$perc10tunf60) / (60 * 60),
      y = 1:length(d$perc10tunf60) / length(d$perc10tunf60),
      sim = "60th (simulated)"))
ggplot(d, aes(x = x, y = y, colour = sim, linetype = sim)) +
geom_line() +
scale_x_continuous(name = paste("\n10th percentile of time until next",
  "failure in hours"),
  breaks = seq(0, max(d$x, na.rm = TRUE), 24),
  minor = seq(0, max(d$x, na.rm = TRUE), 6),
  limits = c(0, max(d$x, na.rm = TRUE))) +
scale_y_continuous(name = paste("Cumulative fraction  \nof",
  "consensuses  \nfrom July to  \nDecember 2010  "),
  formatter = "percent", limits = c(0, 1)) +
scale_colour_manual(name = paste("WMTBF percentile\nfor",
  "assigning\nStable flag\n"),
    values = c("60th (simulated)" = "black",
      "50th (simulated)" = "grey45", "50th (observed)" = "black",
      "40th (simulated)" = "grey60", "30th (simulated)" = "grey80")) +
scale_linetype_manual(name = paste("WMTBF percentile\nfor",
  "assigning\nStable flag\n"),
  values = c(1, 3, 1, 1, 1)) +
opts(plot.title = theme_text(size = 14 * 0.8, face = "bold"),
  axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "wmtbf-tunf-sim.pdf", width = 8, height = 4, dpi = 100)

d <- data
d <- d[d$time < '2010-06-26 00:00:00' | d$time > '2010-06-28 23:00:00', ]
d <- d[d$time < '2010-01-25 00:00:00' | d$time > '2010-01-26 23:00:00', ]
d <- rbind(
  data.frame(x = sort(d$perc10wfu30wfu50advbw) / 10000,
      y = 1:length(d$perc10wfu30wfu50advbw) /
          length(d$perc10wfu30wfu50advbw),
      sim = "30th (simulated)"),
  data.frame(x = sort(d$perc10wfu40wfu50advbw) / 10000,
      y = 1:length(d$perc10wfu40wfu50advbw) /
          length(d$perc10wfu40wfu50advbw),
      sim = "40th (simulated)"),
  data.frame(x = sort(d$perc10wfu) / 10000,
      y = 1:length(d$perc10wfu) / length(d$perc10wfu),
      sim = "50th (observed)"),
  data.frame(x = sort(d$perc10wfu50wfu50advbw) / 10000,
      y = 1:length(d$perc10wfu50wfu50advbw) /
          length(d$perc10wfu50wfu50advbw),
      sim = "50th (simulated)"),
  data.frame(x = sort(d$perc10wfu60wfu50advbw) / 10000,
      y = 1:length(d$perc10wfu60wfu50advbw) /
          length(d$perc10wfu60wfu50advbw),
      sim = "60th (simulated)"))
ggplot(d, aes(x = x, y = y, colour = sim, linetype = sim)) +
geom_line() +
scale_x_continuous(name = "\n10th percentile of WFU in the future",
  formatter = "percent") +
scale_y_continuous(name = paste("Cumulative fraction  \nof",
  "consensuses  \nfrom July to  \nDecember 2010  "),
  formatter = "percent", limits = c(0, 1)) +
scale_colour_manual(name = paste("WFU percentile\nfor",
  "assigning\nGuard flag\n"),
    values = c("60th (simulated)" = "black",
      "50th (simulated)" = "grey45", "50th (observed)" = "black",
      "40th (simulated)" = "grey60", "30th (simulated)" = "grey80")) +
scale_linetype_manual(name = paste("WFU percentile\nfor",
  "assigning\nGuard flag\n"),
  values = c(1, 1, 3, 1, 1)) +
opts(plot.title = theme_text(size = 14 * 0.8, face = "bold"),
  axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "wfu-wfu-sim.pdf", width = 8, height = 4, dpi = 100)

d <- data
d <- rbind(
  data.frame(x = sort(d$perc10fwb50wfu40advbw),
      y = 1:length(d$perc10fwb50wfu40advbw) /
          length(d$perc10fwb50wfu40advbw),
      sim = "40th (simulated)"),
  data.frame(x = sort(d$perc10fwb50wfu50advbw),
      y = 1:length(d$perc10fwb50wfu50advbw) /
          length(d$perc10fwb50wfu50advbw),
      sim = "50th (simulated)"),
  data.frame(x = sort(d$perc10fwb),
      y = 1:length(d$perc10fwb) /
          length(d$perc10fwb),
      sim = "50th (observed)"),
  data.frame(x = sort(d$perc10fwb50wfu60advbw),
      y = 1:length(d$perc10fwb50wfu60advbw) /
          length(d$perc10fwb50wfu60advbw),
      sim = "60th (simulated)"),
  data.frame(x = sort(d$perc10fwb50wfu70advbw),
      y = 1:length(d$perc10fwb50wfu70advbw) /
          length(d$perc10fwb50wfu70advbw),
      sim = "70th (simulated)"))
ggplot(d, aes(x = x / 1024, y = y, linetype = sim, colour = sim)) +
geom_line() +
scale_x_continuous(name = paste("\n10th percentile of weighted bandwidth",
  "in KiB/s in the future")) +
scale_y_continuous(name = paste("Cumulative fraction  \nof",
  "consensuses  \nfrom July to  \nDecember 2010  "),
  formatter = "percent", limits = c(0, 1)) +
scale_colour_manual(name = paste("Advertised\nbandwidth\npercentile\nfor",
  "assigning\nGuard flag\n"),
    values = c(
      "40th (simulated)" = "grey80",
      "50th (observed)" = "black",
      "50th (simulated)" = "grey60",
      "60th (simulated)" = "grey45",
      "70th (simulated)" = "black")) +
scale_linetype_manual(name = paste("Advertised\nbandwidth\n",
  "percentile\nfor assigning\nGuard flag\n", sep = ""),
  values = c(1, 1, 3, 1, 1)) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "advbw-wb-sim.pdf", width = 8, height = 4, dpi = 100)

d <- data
d <- aggregate(d[, 2:length(d)], by = list(date = as.Date(d$time)),
  mean)
d <- rbind(
  data.frame(x = d$date, y = d$guard / d$running,
    variable = "50th (observed)"),
  data.frame(x = d$date, y = d$guard50wfu50advbw / d$running,
    variable = "50th (simulated)"),
  data.frame(x = d$date, y = d$guard50wfu60advbw / d$running,
    variable = "60th (simulated)"),
  data.frame(x = d$date, y = d$guard50wfu70advbw / d$running,
    variable = "70th (simulated)"))
ggplot(d, aes(x = x, y = y, colour = variable, linetype = variable)) +
geom_line() +
scale_y_continuous(name = "Fraction of   \nRunning relays   ",
  formatter = "percent", limits = c(0, max(d$y, na.rm = TRUE))) +
scale_x_date(name = "", major = "3 months", minor = "1 month",
  format = "%b %Y") +
scale_colour_manual(name = paste("Advertised\nbandwidth\npercentile\nfor",
  "assigning\nGuard flag\n"),
    values = c(
      "50th (observed)" = "black",
      "50th (simulated)" = "grey60",
      "60th (simulated)" = "grey45",
      "70th (simulated)" = "black")) +
scale_linetype_manual(name = paste("Advertised\nbandwidth\npercentile\nfor",
  "assigning\nGuard flag\n"),
  values = c(3, 1, 1, 1)) +
opts(axis.title.x = theme_text(size = 12 * 0.8, face = "bold",
  hjust = 0.5),
  axis.title.y = theme_text(size = 12 * 0.8, face = "bold", vjust = 0.5,
  hjust = 1))
ggsave(filename = "advbw-frac-relays-sim.pdf", width = 8, height = 4,
  dpi = 100)

