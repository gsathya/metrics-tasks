library(ggplot2)
library(scales)

u <- read.csv("bridge-users.csv", stringsAsFactors = FALSE)
u <- u[u$date >= "2009-07-01" & u$date < "2011-08-01", c("date", "cn")]
ggplot(u, aes(x = as.Date(date), y = cn)) +
geom_line(size = 0.75) +
geom_rect(aes(NULL, NULL, xmin = as.Date("2010-01-01"),
    xmax = as.Date("2010-06-30"), ymin = -Inf, ymax = Inf, fill = TRUE)) +
scale_fill_manual(name = "", breaks = TRUE,
  values = alpha("purple", 0.005)) +
scale_x_date("", breaks = "6 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_y_continuous(name = "", limits = c(0, max(u$cn, na.rm = TRUE))) +
opts(legend.position = "none")
ggsave("bridge-users.pdf", width = 8, height = 4, dpi = 150)

b <- read.csv("bridge-blockings.csv", stringsAsFactors = FALSE)
b <- b[b$date >= '2010-01-01' & b$date <= '2010-06-30', ]
fingerprints <- unique(b[b$ips >= 100, "fingerprint"])
b <- b[b$fingerprint %in% fingerprints, ]

d <- data.frame(date = b$date, blocked = ifelse(b$ips < 40, 1, NA))
d <- na.omit(d)
d <- aggregate(d$blocked, by = list(date = d$date), sum)
e <- as.Date(setdiff(seq(from = as.Date("2010-01-01"),
    to = as.Date("2010-06-30"), by = "1 day"), as.Date(d$date)),
    origin = "1970-01-01")
u <- u[u$date >= '2010-01-01' & u$date <= '2010-06-30', ]
d <- rbind(data.frame(date = u$date, value = u$cn, variable = "Users"),
    data.frame(date = d$date, value = d$x, variable = "Blocked Bridges"),
    data.frame(date = as.character(e), value = 0,
    variable = "Blocked Bridges"))
ggplot(d, aes(x = as.Date(date), y = value)) +
geom_line(size = 0.75) +
facet_grid(variable ~ ., scales = "free_y") +
scale_x_date("", breaks = "1 months", minor_breaks = "1 month",
  labels = date_format("%b %Y")) +
scale_y_continuous(name = "")
ggsave("bridge-users-blockings.pdf", width = 8, height = 4, dpi = 150)

b <- data.frame(date = as.Date(b$date), ips = b$ips,
    fingerprint = substr(b$fingerprint, 1, 4),
    blocked = ifelse(b$blocked, "red", "black"))
bb <- b
bb[!is.na(b$ips) & b$ips >= 36, "ips"] <- NA
ggplot(b, aes(x = date, y = ips)) +
facet_wrap(~ fingerprint, ncol = 4) +
geom_line(size = 0.75) +
geom_point(size = 0.75) +
geom_hline(yintercept = 32, linetype = 2, size = 0.25) +
geom_point(data = bb, aes(x = date, y = ips), colour = "red", size = 3,
    alpha = 0.25) +
scale_x_date("", breaks = "2 months", minor_breaks = "1 month",
  labels = date_format("%b")) +
scale_y_continuous(name = "", breaks = c(0, 500, 1000)) +
scale_colour_manual(breaks = c("red", "black"),
    values = c("red", "black")) +
opts(legend.position = "none")
ggsave("bridge-blockings.pdf", height = 9, width = 8)

